
import { memo, useState, useEffect } from 'react';

import { Button, FormControl, FormLabel, FormControlLabel,
		 Radio, RadioGroup, TextField } from '@mui/material';

import { serverFetchUser, serverFetchUpload } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function UploadPane({ context, rkey }) {

  const [userInfo, setUserInfo] = useState(undefined);
  const [confirmation, setConfirmation] = useState(undefined);
  const [error,setError] = useState(undefined);
  const [startUpload, setStartUpload] = useState(false);

  const [userId, setUserId] = useState('');
  const [contextName, setContextName] = useState(context === undefined ? '' : context);
  const [repertoireName, setRepertoireName] = useState('');
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

	const loadUserInfo = async () => {

	  serverFetchUser() 
		.then(result => {
		  setUserInfo(result);
		  if (result.CanUploadToAnyUserId) setUserId(result.AssumeUserId);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error fetching user details');
		});
	}

	loadUserInfo();
	
  }, []);

  useEffect(() => {

	if (!startUpload || confirmation) return;
	
	const uploadFile = async () => {

	  serverFetchUpload(userId, contextName, repertoireName, file)
		.then(result => {
		  setConfirmation(result);
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
		
		{ userInfo.CanUploadToAnyUserId &&
		  
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

  // +--------+
  // | render |
  // +--------+

  return(
	
	<div className={styles.container}>

	  { userInfo && !confirmation && !error &&
		renderUploadForm() }
	  
	  { startUpload && !confirmation && !error &&
		renderMsg('Uploading...', false) }

	  { error &&
		renderMsg(`An error occurred: ${error}.`, true) }
	  
	  { confirmation &&
		renderMsg(`Successfully uploaded ${confirmation.TotalUniques} ` +
				  `unique sequences to repertoire ${confirmation.Name}.`, true) }

	</div>
	
  );
}

)
