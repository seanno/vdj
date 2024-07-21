
import { useState } from 'react';
import { Button, Modal, TextField } from '@mui/material';
import ContextPicker from './ContextPicker.jsx';
import RepertoirePicker from './RepertoirePicker.jsx';

export default function NavigationBar({ user, addTab, clearTabs, showError, refreshCounter }) {

  const [selectedContext, setSelectedContext] = useState(undefined);
  const [selectedRepertoires,setSelectedRepertoires] = useState([]);

  function onContextChange(newContext) {
	setSelectedContext(newContext);
	setSelectedRepertoires([]);
	clearTabs();
  }

  function onRepertoiresChange(newRepertoires) {
	setSelectedRepertoires(newRepertoires);
  }

  function selectionCount() {
	return(selectedRepertoires.length);
  }

  function logout(evt) {
	evt.preventDefault();
	window.location = '/__logout';
  }

  function openDetails() {

	const newTab = {
	  view: 'details',
	  name: `Details - ${selectedRepertoires[0].Name}`,
	  context: selectedContext,
	  repertoire: selectedRepertoires[0]
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
  
  function openTopX() {

	const newTab = {
	  view: 'topx',
	  name: `Top 100 - ${selectedRepertoires[0].Name}`,
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

  function openUpload() {

	const newTab = {
	  view: 'upload',
	  name: 'Upload',
	  context: selectedContext
	};

	addTab(newTab);
  }

  function openAgate() {

	const newTab = {
	  view: 'agate',
	  name: 'Import',
	  context: selectedContext
	};

	addTab(newTab);
  }

  function openDelete() {

	const newTab = {
	  view: 'delete',
	  name: 'Delete',
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
		refreshCounter={ refreshCounter }
		ShowError={ showError }
	  />

	  { selectedContext &&

		<>
		  <div>
			<Button
			  variant='contained'
			  sx={{ mr: 1, mb: 1 }}
			  disabled={selectionCount() !== 1}
			  onClick={openTopX}>
			  Top 100
			</Button>

			<Button
			  variant='contained'
			  sx={{ mr: 1, mb: 1 }}
			  disabled={selectionCount() !== 1}
			  onClick={openDetails}>
			  Details
			</Button>
		  </div>

		  <div>
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
			  disabled={selectionCount() < 2 || selectionCount() > window.overlapMaxSamples}
			  onClick={openOverlap}>
			  Overlap
			</Button>
		  </div>

		</>
	  }

	  <br/>
	  
	  <Button
		variant='contained'
		sx={{ mr: 1, mb: 1  }}
		onClick={openUpload}>
		Upload
	  </Button>

	  { user.AgateEnabled &&

		<Button
		  variant='contained'
		  sx={{ mr: 1, mb: 1 }}
		  onClick={openAgate}>
		  Import
		</Button>

	  }

	  { selectedContext &&
		
		<Button
		  variant='contained'
		  sx={{ mr: 1, mb: 1 }}
		  disabled={selectionCount() < 1}
		  onClick={openDelete}>
		  Delete
		</Button>
	  }

	  <div style={{ 'marginTop': '30px' }}>
		<a title={user.Id} href="#" onClick={(evt) => logout(evt)}>logout</a>
	  </div>
	  
	</div>
	
  );
}

