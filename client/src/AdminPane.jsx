
import { memo, useState, useEffect } from 'react';

import { Button, TextField } from '@mui/material';

import { serverFetchAdmin } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function AdminPane({ user, context, repertoires, rkey }) {

  const [operation, setOperation] = useState('');
  const [body, setBody] = useState('');
  const [go, setGo] = useState(false);

  const [response, setResponse] = useState('');
  
  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!go) return;
	
	const postAdmin = async () => {

	  setGo(false);
	  setResponse('');

	  serverFetchAdmin(operation, body)
		.then(result => {
		  setResponse(JSON.stringify(result, null, 2));
		})
		.catch(error => {
		  setResponse('' + error);
		});
	}

	postAdmin();
	
  }, [operation, body, go]);

  // +--------+
  // | render |
  // +--------+

  return(
	<div className={styles.container}>

	  <div className={styles.dialogTxt}>
		<TextField
		  label='Operation'
		  variant='outlined'
		  value={operation}
		  sx={{ width: '100%' }}
		  onChange={(evt) => setOperation(evt.target.value)}
		/>
	  </div>
	  
	  <div className={styles.dialogTxt}>
		<TextField
		  label='Body'
		  variant='outlined'
		  multiline
		  value={body}
		  sx={{ width: '100%' }}
		  maxRows={8}
		  onChange={(evt) => setBody(evt.target.value)}
		/>
	  </div>

	  <div>
		<Button
		  variant='outlined'
		  disabled={!operation}
		  onClick={() => setGo(true)} >
		  Go
		</Button>
	  </div>

	  { response &&
		<div>
		  <pre><code>{ response }</code></pre>
		</div>
	  }

	</div>
  );
}

)
