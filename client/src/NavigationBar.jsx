
import { useState, useEffect } from 'react';
import { Autocomplete, Button, Modal, TextField } from '@mui/material';
import RepertoirePicker from './RepertoirePicker.jsx';

import { serverFetchContexts, serverFetchRepertoires } from './lib/server.js';

export default function NavigationBar({ user, addTab, clearTabs, refreshCounter }) {

  const [contextList, setContextList] = useState(undefined);
  const [selectedContext, setSelectedContext] = useState(undefined);
  
  const [repertoireList,setRepertoireList] = useState(undefined);
  const [selectedRepertoires,setSelectedRepertoires] = useState([]);

  const [error, setError] = useState(undefined);

  // +--------------+
  // | Context List |
  // +--------------+

  useEffect(() => {

	const loadContexts = async () => {
	  serverFetchContexts()
		.then(result => {
		  setContextList(result);
		  verifySelectedContext(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error loading contexts');
		  selectContext(undefined);
		});
	}

	loadContexts();
	
  }, [refreshCounter]);

  function verifySelectedContext(newContexts) {
	if (selectedContext && !newContexts.find((c) => c === selectedContext)) {
	  selectContext(undefined);
	}
  }
  
  function selectContext(newContext) {
	setSelectedContext(newContext);
	setRepertoireList(undefined);
	setSelectedRepertoires([]);
	clearTabs();
  }
  
  // +-----------------+
  // | Repertoire List |
  // +-----------------+

  useEffect(() => {

	if (!selectedContext) {
	  setRepertoireList(undefined);
	  setSelectedRepertoires([]);
	  return;
	}

	const loadRepertoires = async () => {
	  serverFetchRepertoires(selectedContext)
		.then(result => {
		  setRepertoireList(result);
		  verifySelectedRepertoires();
		})
		.catch(error => {
		  console.error(error);
		  setRepertoireList(undefined);
		  showError('Error loading repertoires');
		});
	}

	loadRepertoires();
	
  }, [selectedContext, refreshCounter]);

  function verifySelectedRepertoires() {
	// nyi - for now just unselect all
	setSelectedRepertoires([]);
  }

  function changeSelectedRepertoires(newRepertoires) {
	setSelectedRepertoires(newRepertoires);
  }

  function selectionCount() {
	return(selectedRepertoires.length);
  }

  // +---------------------+
  // | Button/Link Actions |
  // +---------------------+

  function openDetails() {

	const newTab = { view: 'details',
					 name: `Details - ${selectedRepertoires[0].Name}`,
					 context: selectedContext,
					 repertoire: selectedRepertoires[0] };
	addTab(newTab);
  }

  function openSearch() {

	const newTab = { view: 'search', name: 'Search',
					 context: selectedContext,
					 repertoires: selectedRepertoires };
	addTab(newTab);
  }
  
  function openTopX() {

	const newTab = { view: 'topx',
					 name: `Top 100 - ${selectedRepertoires[0].Name}`,
					 context: selectedContext,
					 repertoire: selectedRepertoires[0] };
	addTab(newTab);
  }

  function openOverlap() {

	const newTab = { view: 'overlap', name: 'Overlap',
					 context: selectedContext,
					 repertoires: selectedRepertoires };
	addTab(newTab);
  }

  function openTracking() {

	const newTab = { view: 'track', name: 'Tracking',
					 context: selectedContext,
					 repertoires: selectedRepertoires };
	addTab(newTab);
  }

  function openGeneUse() {

	const newTab = { view: 'genes', 
					 name: `Gene Use - ${selectedRepertoires[0].Name}`,
					 context: selectedContext,
					 repertoire: selectedRepertoires[0] };
	addTab(newTab);
  }

  function openUpload() {

	const newTab = { view: 'upload', name: 'Upload',
					 context: selectedContext };
	addTab(newTab);
  }

  function openAgate() {

	const newTab = { view: 'agate', name: 'Import',
					 context: selectedContext };
	addTab(newTab);
  }

  function openDelete() {

	const newTab = { view: 'delete', name: 'Delete',
					 context: selectedContext,
					 repertoires: selectedRepertoires };
	addTab(newTab);
  }

  function openExport() {

	const newTab = { view: 'export',
					 name: `Export - ${selectedRepertoires[0].Name}`,
					 context: selectedContext,
					 repertoire: selectedRepertoires[0] };
	addTab(newTab);
  }

  function logout(evt) {
	evt.preventDefault();
	window.location = user.LogoutPath;
  }

  function openAdmin(evt) {
	evt.preventDefault();

	const newTab = { view: 'admin', name: 'Admin',
					 user: user,
					 context: selectedContext,
					 repertoires: selectedRepertoires };

	addTab(newTab);
  }

  // +--------+
  // | Render |
  // +--------+

  return(
	
    <div>
	  
	  { /* ----- ERROR */
		error &&
		
		<p>{error}</p>
	  }
	  
	  { /* ----- CONTEXT LIST */
		contextList &&
		
		<Autocomplete
		  disablePortal
		  sx={{ width: '100%' }}
		  options={ contextList }
		  value={ selectedContext ? selectedContext : null }
		  onChange={ (evt, newValue) => selectContext(newValue) }
		  renderInput={ (params) => <TextField {...params} label="Context" /> }
		/>
	  }
	  
	  { /* ----- REPERTOIRE LIST */
		repertoireList &&
		
		<RepertoirePicker
		  repertoireList={ repertoireList }
		  selectedRepertoires={ selectedRepertoires }
		  changeSelection={ changeSelectedRepertoires }
		/>
	  }

	  { /* ----- BUTTONS */ }

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

		  <div>
			<Button
			  variant='contained'
			  sx={{ mr: 1, mb: 1 }}
			  disabled={selectionCount() < 1}
			  onClick={openTracking}>
			  Track
			</Button>
			
			<Button
			  variant='contained'
			  sx={{ mr: 1, mb: 1 }}
			  disabled={selectionCount() !== 1}
			  onClick={openGeneUse}>
			  Genes
			</Button>
		  </div>
		  
		  <div>
			<Button
			  variant='contained'
			  sx={{ mr: 1, mb: 1 }}
			  disabled={selectionCount() !== 1}
			  onClick={openExport}>
			  Export
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

	  { /* ----- MISC LINKS */ }
		
	  <div style={{ 'marginTop': '30px' }}>
		<a title={user.AuthUserId} href="#" onClick={(evt) => logout(evt)}>logout</a>
	  </div>

	  <div>
		<a target="_blank" href="https://docs.google.com/viewer?url=https://github.com/seanno/vdj/raw/main/docs/VDJ%20User%20Manual.pdf">help</a>
	  </div>

	  { user.IsAdmin &&
		<div>
		  <a href="#" onClick={(evt) => openAdmin(evt)}>admin</a>
		</div>
	  }

	</div>
	
  );
}

