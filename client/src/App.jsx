
import { useState } from 'react';
import { IconButton, Snackbar, Tabs, Tab } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';

import NavigationBar from "./NavigationBar.jsx";
import DetailsPane from "./DetailsPane.jsx";
import OverlapPane from "./OverlapPane.jsx";
import SearchPane from "./SearchPane.jsx";

import styles from './App.module.css'

export default function App() {

  const [error,setError] = useState(undefined);
  const [tabs, setTabs] = useState([]);
  const [tabValue, setTabValue] = useState(undefined);

  function showError(msg) {
	setError(msg);
  }

  function addTab(t) {

	var i = 0;
	const originalName = t.name;
	while (tabNameExists(t.name)) {
	  t.name = `${originalName} (${++i})`;
	}

	setTabs([t, ...tabs]);
	setTabValue(t);
  }

  function removeTab(t) {
	for (var i = 0; i < tabs.length; ++i) {
	  if (tabs[i].name === t.name) {
		const newTabs = [... tabs];
		newTabs.splice(i, 1);
		setTabs(newTabs);
		setTabValue(newTabs.length === 0 ? undefined : newTabs[0]);
		return;
	  }
	}
  }

  function tabNameExists(name) {
	for (var i = 0; i < tabs.length; ++i) {
	  if (tabs[i].name === name) return(true);
	}
	return(false);
  }

  function handleTabChange(evt, newValue) {
	setTabValue(newValue);
  };

  // +--------+
  // | render |
  // +--------+

  function renderTabContent() {

	return(
	  <>
		<Tabs
		  value={tabValue}
		  onChange={handleTabChange}
		  orientation='horizontal'
		  variant='scrollable'>
		  {
			tabs.map((t) => {
			  return(<Tab
					   key={`tabtab-${t.name}`}
					   value={t}
					   label={
						 <span>
						   {t.name}
						   <CloseIcon
							 onClick={(evt) => { evt.stopPropagation(); removeTab(t); } }
							 sx={{ fontSize: '12px',
								   border: '1px solid #1976d2',
								   color: '#1976d2',
								   marginLeft: '8px',
								   marginBottom: '-1px' }} />
						 </span>
					   }
					 />);
			})
		  }
		</Tabs>

		{
		  tabs.map((t) => {
			return(
			  <div className={styles.content}
				   key={`tabcontent-${t.name}`}
				   style={{ display: t.name === tabValue.name ? 'block' : 'none' }}>

				{ t.view === 'details' && <DetailsPane
											context={t.context}
											repertoire={t.repertoire}
											rkey={`p-${t.name}`} /> }

				{ t.view === 'overlap' && <OverlapPane
											context={t.context}
											repertoires={t.repertoires} 
											rkey={`p-${t.name}`} /> }

				{ t.view === 'search' && <SearchPane
											context={t.context}
											repertoires={t.repertoires} 
											rkey={`p-${t.name}`} /> }
			  </div>
			);
		  })
		}

 	  </>
	);
  }
  
  return(
	
    <div className={styles.main}>

	  <div className={styles.nav}>
		<NavigationBar addTab={addTab} showError={showError} />
	  </div>

	  <div className={styles.tabs}>
		{ tabs.length > 0 && renderTabContent() }
	  </div>
	  
	  <Snackbar
		open={error !== undefined}
		autoHideDuration={ 2500 }
		message={error}
		anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
		onClose={ () => setError(undefined) }
	  />

	</div>
	
  );
}

