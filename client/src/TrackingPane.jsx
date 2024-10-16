
import { memo, useState, useEffect } from 'react';
import { Button } from '@mui/material';

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchDxOptions, serverFetchTracking } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function TrackingPane({ context, repertoires, rkey }) {

  const [dxOptions, setDxOptions] = useState(undefined);
  const [startTracking, setStartTracking] = useState(false);
  const [trackingResults, setTrackingResults] = useState(undefined);
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

  useEffect(() => {

	if (!startTracking) return;

	const trackStuff = async () => {

	  const rearrangements = [];
	  for (var i = 0; i < dxOptions.length; ++i) {
		if (!dxOptions[i].SelectionIndices) continue;
		for (var j = 0; j < dxOptions[i].SelectionIndices.length; ++j) {
		  const k = dxOptions[i].SelectionIndices[j];
		  rearrangements.push(dxOptions[i].Rearrangements[k]);
		}
	  }
	
	  serverFetchTracking(context, repertoires, rearrangements) 
		.then(result => {
		  setTrackingResults(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error fetching tracking report');
		});
	}

	trackStuff();
	
  }, [startTracking]);

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
		  disabled={!somethingSelected()}
		  onClick={() => setStartTracking(true)} >
		  Go
		</Button>
	  </>
	);
  }
  
  // +---------------+
  // | renderResults |
  // +---------------+

  function renderResults() {
	// nyi
	return(<pre><code>{ JSON.stringify(trackingResults, null, 2) }</code></pre>);
  }
  
  // +---------------+
  // | renderLoading |
  // +---------------+

  function renderLoading() {
	return(<div>Tracking...</div>);
  }

  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  { !startTracking && dxOptions && renderOptions() }
	  { startTracking && !trackingResults && renderLoading() }
	  { startTracking && trackingResults && renderResults() }
	  { error && <div className={styles.hdr}>{error}</div> }

	</div>
  );
}

)
