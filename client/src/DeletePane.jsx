
import { memo, useState, useEffect } from 'react';
import { Button } from '@mui/material';

import { serverFetchDelete } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function DeletePane({ context,
										  repertoires,
										  rkey }) {

  const [startDelete, setStartDelete] = useState(false);
  const [response, setResponse] = useState(undefined);

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!startDelete) return;
	
	const deleteRepertoires = async () => {

	  serverFetchDelete(context, repertoires) 
		.then(result => {
		  setResponse(result);
		})
		.catch(error => {
		  console.error(error);
		  setResponse('Error deleting repertoire(s)');
		});
	}

	deleteRepertoires();
	
  }, [startDelete, context, repertoires]);

  // +---------------------+
  // | renderDeleteConfirm |
  // +---------------------+

  function renderDeleteConfirm() {
	return(
	  <>
		<div className={styles.hdr}>Delete: { repertoires.map((r) => r.Name).join(', ') }</div>
		
		<Button
		  variant='contained'
		  onClick={() => setStartDelete(true)}>
		  YES, delete { repertoires.length > 1 ? 'these repertoires' : 'this repertoire' }
		</Button>
	  </>
	);
  }

  // +----------------+
  // | renderDeleting |
  // +----------------+

  function renderDeleting() {
	return(<div className={styles.hdr}>Deleting...</div>);
  }

  // +----------------+
  // | renderResponse |
  // +----------------+

  function renderResponse() {
	
	const elts = response.map((r, i) => <li key={`${rkey}-li-${i}`}>{r.Name}: {r.Result}</li>);
	
	return(
	  <>
		<ul>{elts}</ul>

		<div className={styles.hdr}>
		  Close this tab with the [X] icon above.
		</div> 
	  </>
	);
  }

  // +--------+
  // | render |
  // +--------+

  return(
	
	<div className={styles.container}>

	  { !response && !startDelete && renderDeleteConfirm() }
	  { !response &&  startDelete && renderDeleting() }
	  { response && renderResponse() }

	</div>
	
  );
}

)
