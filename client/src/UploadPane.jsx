
import { memo, useState, useEffect } from 'react';

import { Button, FormControl, FormLabel, FormControlLabel,
		 Radio, RadioGroup, TextField } from '@mui/material';

import { serverFetchUpload } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function UploadPane({ user, context, refresh, rkey }) {

  const [confirmation, setConfirmation] = useState(undefined);
  const [error,setError] = useState(undefined);
  const [startUpload, setStartUpload] = useState(false);

  const [userId, setUserId] = useState(user.CanUploadToAnyUserId ? user.AssumeUserId : '');
  const [contextName, setContextName] = useState(context === undefined ? '' : context);
  const [repertoireName, setRepertoireName] = useState('');
  const [date, setDate] = useState('');
  const [file, setFile] = useState(undefined);

  function onFileChange(newFile) {
	setFile(newFile);
	if (!repertoireName) {
	  var newName = newFile.name;
	  const ichDot = newName.indexOf(".");
	  if (ichDot !== -1) newName = newName.substring(0, ichDot);
	  setRepertoireName(newName);
	}
  }
  
  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!startUpload || confirmation) return;
	
	const uploadFile = async () => {

	  serverFetchUpload(userId, contextName, repertoireName, file, date)
		.then(result => {
		  if (result.httpStatus && result.httpStatus === 409) {
			setError('Repertoire already exists');
		  }
		  else {
			setConfirmation(result);
			refresh();
		  }
		})
		.catch(error => {
		  console.error(error);
		  setError('Error uploading repertoire');
		});
	}

	uploadFile();
	
  }, [userId, contextName, repertoireName, file, startUpload]);
  

  // +------------------+
  // | renderUploadForm |
  // +------------------+

  function renderUploadForm() {

	const readyToUpload = (contextName && contextName.length > 0 &&
						   repertoireName && repertoireName.length > 0 &&
						   file !== undefined);
	
	return(
	  <>
		
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
		
		<div className={styles.dialogTxt}>
		  <TextField
			label='Repertoire Name'
			variant='outlined'
			value={repertoireName}
			sx={{ width: '100%' }}
			onChange={(evt) => setRepertoireName(evt.target.value)}
		  />
		</div>

		<div className={styles.dialogTxt}>
		  <TextField
			type='date'
			label='Collection / Sample Date (Optional)'
			value={date}
			InputLabelProps= {{ shrink: true }}
			sx={{ width: '100%' }}
			onChange={(evt) => setDate(evt.target.value)}
		  />
		</div>
		
		<div className={styles.dialogTxt}>
		  <input
			type="file"
			onChange={(evt) => onFileChange(evt.target.files[0]) } />
		</div>

		<Button
		  variant='outlined'
		  disabled={!readyToUpload}
		  onClick={() => setStartUpload(true)} >
		  Go
		</Button>
	  </>
	);

  }
  
  // +-----------------+
  // | renderUploading |
  // +-----------------+

  function renderUploading() {
	return(<div className={styles.hdr}>Uploading...</div>);
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

  // +----------------+
  // | renderComplete |
  // +----------------+

  function renderComplete() {

	const msg = `Successfully uploaded ${confirmation.TotalUniques} ` +
		  `unique sequences to repertoire "${confirmation.Name}".`;

	const xmsg = (contextName === context ? undefined
				  : ` Select "${contextName}" in the top-left dropdown to view your data.`);

	return(
	  <>
		<div>{msg}</div>
		{ xmsg && <div>{xmsg}</div> }
		<br/>
		<div className={styles.hdr}>Close this tab with the [X] icon above.</div> 
	  </>
	);

  }

  // +--------+
  // | render |
  // +--------+

  return(
	
	<div className={styles.container}>

	  { !confirmation && !error &&
		renderUploadForm() }
	  
	  { startUpload && !confirmation && !error &&
		renderMsg('Uploading...', false) }

	  { error &&
		renderMsg(`An error occurred: ${error}.`, true) }
	  
	  { confirmation &&
		renderComplete() }

	</div>
	
  );
}

)
