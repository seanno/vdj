
import { memo, useState, useEffect } from 'react';

import { Button, TextField } from '@mui/material';

import { serverFetchAdmin } from './lib/server.js';

import styles from './Pane.module.css'
import tableStyles from './Tables.module.css'

export default memo(function AdminPane({ user, context, repertoires, rkey }) {

  const [operation, setOperation] = useState('');
  const [body, setBody] = useState('');
  const [go, setGo] = useState(false);

  const [response, setResponse] = useState(undefined);
  
  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!go) return;
	
	const postAdmin = async () => {

	  setGo(false);
	  setResponse(undefined);

	  serverFetchAdmin(operation, body)
		.then(result => {
		  setResponse(result);
		})
		.catch(error => {
		  setResponse({ Error: '' + error});
		});
	}

	postAdmin();
	
  }, [operation, body, go]);

  // +---------------------+
  // | renderResponseTable |
  // +---------------------+

  function getResponseHeaderElts() {
	const hdrs = [];
	for (var i = 0; i < response.Headers.length; ++i) {
	  hdrs.push(<th key={`${rkey}-th-${i}`}>{response.Headers[i]}</th>);
	}
	return(hdrs);
  }

  function getResponseRowElts() {
	
	const rows = []
	if (!response.Rows) return(rows);
	
	for (var i = 0; i < response.Rows.length; ++i) {
	  rows.push(<tr key={`${rkey}-tr-${i}`}>{getResponseFieldElts(response.Rows[i], i)}</tr>);
	}
	return(rows);
	
  }

  function getResponseFieldElts(fields, ifield) {
	
	const elts = [];
	for (var i = 0; i < fields.length; ++i) {
	  elts.push(<td key={`${rkey}-td-${ifield}-${i}`}>{fields[i]}</td>);
	}
	return(elts);
  }
  
  function renderResponseTable() {
	return(
	  <table className={tableStyles.rearrangementsTable} style={{ marginTop: '24px'}} >
		<tr>{ getResponseHeaderElts() }</tr>
		{ getResponseRowElts() }
	  </table>
	);
  }

  // +--------------------+
  // | renderResponseJson |
  // +--------------------+

  function renderResponseJson() {
	return(<pre><code>{ JSON.stringify(response, null, 2) }</code></pre>);
  }

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

	  { response && response.Headers && renderResponseTable() }
	  { response && !response.Headers && renderResponseJson() }

	</div>
  );
}

)
