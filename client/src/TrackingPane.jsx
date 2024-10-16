
import { memo, useState, useEffect } from 'react';
import { Button } from '@mui/material';

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchDxOptions } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function TrackingPane({ context, repertoires, rkey }) {

  const [dxOptions, setDxOptions] = useState(undefined);
  const [error,setError] = useState(undefined);

  function bubbleSelections(rep, newSelections) {

	const newOptions = [...dxOptions];
	
	for (var i = 0; i < newOptions.length; ++i) {
	  if (newOptions[i].Repertoire.Name === rep.Name) {
		newOptions[i].SelectionIndices = newSelections;
		setDxOptions(newOptions);
		return;
	  }
	}
  }

  function somethingSelected() {
	for (var i = 0; i < dxOptions.length; ++i) {
	  if (dxOptions[i].SelectionIndices && dxOptions[i].SelectionIndices.length > 0) {
		return(true);
	  }
	}
	return(false);
  }
  
  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	const loadOptions = async () => {

	  serverFetchDxOptions(context, repertoires) 
		.then(result => {
		  setDxOptions(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error fetching dx options');
		});
	}

	loadOptions();
	
  }, []);

  // +---------------+
  // | renderOptions |
  // +---------------+

  function renderOptions() {

	const tablesJsx = dxOptions.map((rr,irep) => {
	  return(
		<RearrangementsTable
		  repertoire={rr.Repertoire}
		  rearrangements={rr.Rearrangements}
		  caption={rr.Repertoire.Name}
		  key={`${rkey}-tbl-${irep}}`}
		  bubbleSelections={bubbleSelections}
		  initialSelections={rr.SelectionIndices}
		/>
	  );
	});
	
	return(
	  <>
		{tablesJsx}
		<Button
		  variant='contained'
		  sx={{ mr: 1, mb: 1 }}
		  disabled={!somethingSelected()}>
		  Go
		</Button>
	  </>
	);
  }
  
  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  { dxOptions && renderOptions() }
	  { error && <div className={styles.hdr}>{error}</div> }

	</div>
  );
}

)
