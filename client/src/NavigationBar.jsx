
import { useState } from 'react';
import { Button, ButtonGroup, Modal, TextField } from '@mui/material';
import ContextPicker from './ContextPicker.jsx';
import RepertoirePicker from './RepertoirePicker.jsx';

export default function NavigationBar({ addTab, showError }) {

  const [selectedContext, setSelectedContext] = useState(undefined);
  const [selectedRepertoires,setSelectedRepertoires] = useState([]);

  function onContextChange(newContext) {
	setSelectedContext(newContext);
	setSelectedRepertoires([]);
  }

  function onRepertoiresChange(newRepertoires) {
	setSelectedRepertoires(newRepertoires);
  }

  function selectionCount() {
	return(selectedRepertoires.length);
  }

  function openDetails() {

	const newTab = {
	  view: 'details',
	  name: selectedRepertoires[0].Name,
	  context: selectedContext,
	  repertoire: selectedRepertoires[0]
	};

	addTab(newTab);
  }

  function openOverlap() {

	const newTab = {
	  view: 'overlap',
	  name: 'Overlap',
	  context: selectedContext,
	  repertoires: selectedRepertoires
	};

	addTab(newTab);
  }

  function openSearch() {

	const newTab = {
	  view: 'search',
	  name: 'Search',
	  context: selectedContext,
	  repertoires: selectedRepertoires
	};

	addTab(newTab);
  }
  
  // +--------+
  // | Render |
  // +--------+

  return(
	
    <div>

	  <ContextPicker
		onContextChange={ onContextChange }
		showError={ showError }
	  />

	  <RepertoirePicker
		selectedContext={ selectedContext }
		selectedRepertoires={ selectedRepertoires }
		onRepertoiresChange={ onRepertoiresChange }
		ShowError={ showError }
	  />

	  { selectedContext &&

		<>
		  <Button
			variant='contained'
			sx={{ mr: 1, mb: 1 }}
			disabled={selectionCount() !== 1}
			onClick={openDetails}>
			Details
		  </Button>
		  
		  <Button
			variant='contained'
			sx={{ mr: 1, mb: 1 }}
			disabled={selectionCount() < 1}
			onClick={openSearch}>
			Search
		  </Button>

		  <Button
			variant='contained'
			sx={{ mr: 1, mb: 1 }}
			disabled={selectionCount() !== 2}
			onClick={openOverlap}>
			Overlap
		  </Button>

		</>
	  }
		
	</div>
	
  );
}

