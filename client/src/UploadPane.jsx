
import { memo, useState, useEffect } from 'react';

import { Button, FormControl, FormLabel, FormControlLabel,
		 Radio, RadioGroup, Snackbar, TextField } from '@mui/material';

import { serverFetchUser, serverFetchUpload } from './lib/server.js';

import styles from './Upload.module.css'

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

	const readyToUpload = (contextName.length > 0 &&
						   repertoireName.length > 0 &&
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
	return(<div className={styles.msg}>Uploading...</div>);
  }

  // +--------------------+
  // | renderConfirmation |
  // +--------------------+

  function renderConfirmation() {
	return(
	  <>
		<div className={styles.msg}>
		  Successfully uploaded {confirmation.TotalUniques} unique sequences<br/>
		  to repertoire {confirmation.Name}.
		</div>
		<div className={styles.msg}>
		  Close this tab with the [X] icon.
		</div>
	  </>);
  }

  // +--------+
  // | render |
  // +--------+

  return(
	
	<div className={styles.container}>

	  { userInfo && !confirmation && renderUploadForm() }
	  { startUpload && !confirmation && renderUploading() }
	  { confirmation && renderConfirmation() }

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

)
