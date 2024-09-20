
import { memo, useState, useEffect } from 'react';

import { Button, IconButton, InputAdornment,
		 List, ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText, TextField } from '@mui/material';

import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';

import { serverFetchAgateSamples, serverImportAgate } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function AgatePane({ user, context, refresh, rkey }) {

  const [error, setError] = useState(undefined);

  // sample search state
  const [agateUser, setAgateUser] = useState('');
  const [agatePass, setAgatePass] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [searchString, setSearchString] = useState('');
  const [startSearch, setStartSearch] = useState(false);

  const [samples, setSamples] = useState(undefined);
  const [selections, setSelections] = useState(undefined);

  // import state
  const [userId, setUserId] = useState(user.CanUploadToAnyUserId ? user.AssumeUserId : '');
  const [contextName, setContextName] = useState(context === undefined ? '' : context);
  const [startImport, setStartImport] = useState(false);

  // importing state
  const [currentImportIdx, setCurrentImportIdx] = useState(0);
  const [results, setResults] = useState([]);

  // +---------+
  // | actions |
  // +---------+

  function toggleCheckbox(i) {

	const idxSel = findSelectionIndex(i);
	const newSelections = [...selections];
	
	if (idxSel === -1) {
	  newSelections.push(i);
	}
	else {
	  newSelections.splice(idxSel, 1);
	}

	setSelections(newSelections);
  }

  function isChecked(i) {
	return(findSelectionIndex(i) !== -1);
  }
  
  function findSelectionIndex(i) {
	for (var iWalk = 0; iWalk < selections.length; ++iWalk) {
	  if (selections[iWalk] === i) return(iWalk);
	}

	return(-1);
  }

  function selectAll() {
	const newSelections = ((selections.length == samples.length)
						   ? [] : samples.map((s,irow) => irow));
	
	setSelections(newSelections);
  }

  // +-----------+
  // | useEffect |
  // +-----------+

  // search

  useEffect(() => {

	if (!startSearch || samples || error) return;
	
	const searchAgate = async () => {

	  serverFetchAgateSamples(agateUser, agatePass, searchString)
		.then(result => {
		  setSamples(result);
		  setSelections([]);
		  
		  if (!contextName) {
			setContextName(searchString.endsWith("-")
						   ? searchString.substring(0, searchString.length - 1)
						   : searchString);
		  }
		})
		.catch(error => {
		  console.error(error);
		  setError('Error fetching Agate samples');
		});
	}

	searchAgate();
	
  }, [startSearch, agateUser, agatePass, searchString]);

  // imports

  function addResult(sample, msg) {
	const newResults = [...results];
	newResults.push(`${sample.Name}: ${msg}`);
	setResults(newResults);

	const newIdx = currentImportIdx + 1;
	setCurrentImportIdx(newIdx);
	if (newIdx == selections.length) refresh();
  }
  
  useEffect(() => {

	if (!startImport || currentImportIdx == selections.length || error) return;

	const importOneTsv = async () => {

	  const sample = samples[selections[currentImportIdx]];
	  
	  console.log(`Starting import of index ${currentImportIdx}`);
	  
	  serverImportAgate(agateUser, agatePass, contextName, userId, sample)
		.then(result => {
		  const msg = ((result.httpStatus && result.httpStatus === 409)
					   ? 'repertoire already exists'
					   : `successfully uploaded ${result.TotalUniques} unique sequences`);
			
		  addResult(sample, msg);
		})
		.catch(error => {
		  console.error(error);
		  addResult(sample, 'failed importing repertoire');
		});
	}

	importOneTsv();
	
  }, [startImport, agateUser, agatePass, userId, contextName, currentImportIdx]);
  

  // +------------------+
  // | renderSearchForm |
  // +------------------+

  function renderSearchForm() {

	const searchOK = (searchString && searchString.length >= window.agateMinSearchLength);
	
	const readyToSearch = (searchOK &&
						   (!user.AgateUserPassAuth ||
							(agateUser && agateUser.length > 0 && agatePass && agatePass.length > 0)));

	return(
	  <>
		{ user.AgateUserPassAuth &&

		  <>
			<div className={styles.dialogTxt}>
			  <TextField
				label='Agate User'
				variant='outlined'
				value={agateUser}
				sx={{ width: '100%' }}
				onChange={(evt) => setAgateUser(evt.target.value)}
			  />
			</div>
			
			<div className={styles.dialogTxt}>
			  <TextField
				label='Agate Password'
				type={showPass ? 'text' : 'password'}
				variant='outlined'
				value={agatePass}
				sx={{ width: '100%' }}
				onChange={(evt) => setAgatePass(evt.target.value)}

				InputProps={{
				  endAdornment: 
				  <InputAdornment position="end">
					<IconButton
					  onClick={() => setShowPass(!showPass) }
					  onMouseDown={(evt) => evt.preventDefault() }
					  edge="end"
					>
					  {showPass ? <VisibilityOff /> : <Visibility />}
					</IconButton>
				  </InputAdornment>
				}}
			  />
			</div>
		  </>
		}
		
		<div className={styles.dialogTxt}>
		  <TextField
			label='Search'
			variant='outlined'
			value={searchString}
			sx={{ width: '100%' }}
			onChange={(evt) => setSearchString(evt.target.value)}
			helperText={searchOK ? undefined : `at least ${window.agateMinSearchLength} characters required`}
		  />
		</div>
		
		<Button
		  variant='outlined'
		  disabled={!readyToSearch}
		  onClick={() => setStartSearch(true)} >
		  Go
		</Button>
	  </>
	);
  }

  // +------------------+
  // | renderImportForm |
  // +------------------+

  function renderSampleList() {
	
	return(
	  
	  <List
		sx={{ width: '100%', marginBottom: '12px' }}>

		{
		  samples.map((s, irow) => {

			const labelId = `cl-label-${rkey}-${s.Name}`;

			var label = s.Name;
			if (s.Date) {
			  const d = new Date(s.Date.year, s.Date.month - 1, s.Date.day);
			  label += ' (' + d.toLocaleDateString() + ')';
			}
			
			
			return(
			  <ListItem
				key={`cl-item-${rkey}-${s.Name}`}
				disablePadding>

				<ListItemButton
				  rule={undefined}
				  onClick={() => toggleCheckbox(irow) } >

				  <ListItemIcon
					sx={{ minWidth: '20px' }}>
					
					<Checkbox
					  edge='start'
					  checked={isChecked(irow)}
					  tabIndex={-1}
					  disableRipple
					  inputProps={{ 'aria-label': 'controlled', 'aria-labelledby': labelId }}
					  sx={{ padding: '0px' }}
					/>
				  </ListItemIcon>

				  <ListItemText
					sx={{ margin: '0px' }}
					id={labelId}
					primary={label}
				  />
				  
				</ListItemButton>
			  </ListItem>
			);
		  })
		}

	  </List>

	);
  }

  function renderImportForm() {

	const readyToImport = (contextName && contextName.length > 0 &&
						   selections.length > 0);
	
	return(

	  <>
		{ renderSampleList() }
		
		{ user.CanUploadToAnyUserId &&
		  
		  <div className={styles.dialogTxt}>
			<TextField
			  label='User Id (Email)'
			  variant='outlined'
			  value={userId}
			  sx={{ width: '100%' }}
			  onChange={(evt) => setUserId(evt.target.value)}
			/>
		  </div>
		}

		<div className={styles.dialogTxt}>
		  <TextField
			label='Context Name'
			variant='outlined'
			value={contextName}
			sx={{ width: '100%' }}
			onChange={(evt) => setContextName(evt.target.value)}
		  />
		</div>
		
		<Button
		  variant='outlined'
		  sx={{ mr: 1 }}
		  onClick={selectAll} >
		  { selections.length == samples.length ? 'Clear ' : 'Select ' } All
		</Button>

		<Button
		  variant='outlined'
		  disabled={!readyToImport}
		  onClick={() => setStartImport(true)} >
		  Import
		</Button>
		
	  </>
	);
  }

  // +-----------------+
  // | renderImporting |
  // +-----------------+

  function renderImporting() {

	// NYI --- decide how to deal with "already in the repertoire" issue. starting
	// position: just recognize "already exists" error and message that if we get it
	// we can do better in certain cases (when we are the saveuserid and we know the
	// context) but that gets tricky so save it for another time. maybe add a "select
	// all" button to make it easier for Lanny & co?

	const preMsg = (currentImportIdx < selections.length
					? renderMsg(`Importing ${samples[currentImportIdx].Name}` +
								` (${currentImportIdx+1} of ${selections.length})...`, false)
					: undefined);

	const postMsg = (currentImportIdx === selections.length
					 ? renderMsg('Import complete.', true)
					 : undefined);

	const resultsMsgs = results.map((r, irow) => {
	  return(<div key={`result-${rkey}-${irow}`}>{r}</div>);
	});


	return(
	  <>
		{ preMsg }
		{ resultsMsgs }
		{ postMsg }
	  </>
	);
  }

  // +-----------+
  // | renderMsg |
  // +-----------+

  function renderMsg(msg, showClose) {
	return(
	  <>
		<div className={styles.hdr}>{msg}</div>
		{ showClose && <div className={styles.hdr}>Close this tab with the [X] icon above.</div> }
	  </>
	);
  }

  // +--------+
  // | render |
  // +--------+

  var elts = undefined;

  if (error) {
	// error state
	elts = renderMsg(`An error occurred: ${error}.`, true);
  }
  else if (startImport) {
	// importing
	elts = renderImporting();
  }
  else if (startSearch) {
	if (samples && samples.length > 0) {
	  // useful sample list & import form
	  elts = renderImportForm();
	}
	else if (samples) {
	  // empty sample list
	  elts = renderMsg('No matching samples found', true);
	}
	else {
	  // searching
	  elts = renderMsg('Searching for samples (this can take awhile) ...', false);
	}
  }
  else {
	// search form
	elts = renderSearchForm();
  }
  
  
  return(<div className={styles.container}><form>{elts}</form></div>);
}

)
