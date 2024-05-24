
import { memo, useState, useEffect } from 'react';
import { Button, Snackbar } from '@mui/material';
import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchRepertoire } from './lib/server.js';

export default memo(function DetailsPane({ context, repertoire, rkey }) {

  const [error,setError] = useState(undefined);
  const [rows, setRows] = useState(undefined);
  const [start, setStart] = useState(0);

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	const loadRows = async () => {
	  
	  serverFetchRepertoire(context, repertoire.Name, start, window.detailsPageSize)
		.then(result => {
		  setRows(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error loading repertoire');
		});
	}

	loadRows();
	
  }, [context, repertoire, start]);

  // +--------------+
  // | renderPaging |
  // +--------------+

  function renderPaging() {

	const first = start + 1;
	const last = start + rows.length;

	var prev = start - window.detailsPageSize;
	if (prev < 0) prev = -1;
	
	var next = start + window.detailsPageSize;
	if (next >= repertoire.TotalUniques) next = -1;
	
	return(
	  <div>
		Showing rows {first} through {last} of {repertoire.TotalUniques}
		&nbsp;&nbsp;
		{ next !== -1 && <Button onClick={() =>setStart(next)}>forward</Button> }
		{ prev !== -1 && <Button onClick={() =>setStart(prev)}>back</Button> }
	  </div>
	
	);
  }

  // +--------+
  // | render |
  // +--------+

  if (rows === undefined) return(undefined);
  
  return(

	<div>

	  { renderPaging() }
	  <RearrangementsTable rearrangements={rows} rkey={rkey} />

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
