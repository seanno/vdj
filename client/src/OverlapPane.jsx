
import { memo, useState, useEffect } from 'react';
import { Button, FormControl, FormLabel,
		 FormControlLabel, Radio, RadioGroup, } from '@mui/material';

import { serverFetchOverlap } from './lib/server.js';

import styles from './Pane.module.css'
import tableStyles from './Tables.module.css';

export default memo(function OverlapPane({ context, repertoires, addTab, rkey }) {

  const [overlapType, setOverlapType] = useState(window.overlapTypeDefault);
  const [startOverlap, setStartOverlap] = useState(false);

  const [results, setResults] = useState(undefined);
  const [error,setError] = useState(undefined);

  // +------------+
  // | openSearch |
  // +------------+

  function openSearch(key) {

	const newTab = {
	  view: 'search',
	  name: 'Search',
	  context: context,
	  repertoires: repertoires,

	  params: {
		type: overlapType,
		motif: key,
		muts: 0,
		full: true,
		start: true
	  }
	};

	addTab(newTab);
  }

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (!startOverlap || results) return;
	
	const loadResults = async () => {

	  serverFetchOverlap(context, repertoires, overlapType)
		.then(result => {
		  setResults(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error overlapping repertoires');
		});
	}

	loadResults();
	
  }, [context, repertoires, overlapType, startOverlap]);

  // +--------------+
  // | renderParams |
  // +--------------+

  function renderParams() {

	return(
	  <>
		<div className={styles.hdr}>
		  Overlapping: { repertoires.map((r) => r.Name).join(', ') }
		</div>
	  
		<div className={styles.dialogTxt}>
		  <FormControl>
			<RadioGroup
			  row
			  value={overlapType}
			  onChange={(evt) => setOverlapType(evt.target.value)} >
			  <FormControlLabel value='Rearrangement' control={<Radio/>} label='Nucleotide' />
			  <FormControlLabel value='CDR3' control={<Radio/>} label='CDR3' />
			  <FormControlLabel value='AminoAcid' control={<Radio/>} label ='Amino Acid' />
			</RadioGroup>
		  </FormControl>
		</div>

		<Button
		  variant='outlined'
		  onClick={() => setStartOverlap(true)} >
		  Go
		</Button>

	  </>
	);
  }

  // +---------------+
  // | renderLoading |
  // +---------------+

  function renderLoading() {
	return(<div>Overlapping...</div>);
  }

  // +-------------+
  // | renderError |
  // +-------------+

  function renderError() {
	return(<div>{error}</div>);
  }
  
  // +---------------+
  // | renderResults |
  // +---------------+

  function renderResults() {

	const repertoireHeaders = results.Repertoires.map((r, irep) => {
	  return(
		<th key={`${rkey}-hdr-${irep}`}>
		  {r.Name}
		</th>
	  );
	});

	const rows = results.Items.map((item, irow) => {

	  const counts = item.Counts.map((c, ic) => {
		return(
		  <td className={tableStyles.right} key={`$rkey-${irow}-${ic}`}>
			{c}
		  </td>
		);
	  });
									 
	  return(
		<tr key={`${rkey}-row-${irow}`}>
		  <td className={tableStyles.right}><a href="#" onClick={() => openSearch(item.Key)}>{item.Key}</a></td>
		  <td className={tableStyles.right}>{item.PresentIn}</td>
		  <td className={tableStyles.right}>{item.MaxCount}</td>
		  {counts}
		</tr>
	  );
	});
	
	return(
	  <>
		<div className={styles.hdr}>
		  Overlapping: { repertoires.map((r) => r.Name).join(', ') }
		</div>
		
		<table className={tableStyles.rearrangementsTable}>
		  <thead>
			<tr>
			  <th>{overlapType}</th>
			  <th>Present In</th>
			  <th>Max Count</th>
			  { repertoireHeaders }
			</tr>
		  </thead>
		  <tbody>
			{ rows }
		  </tbody>
		</table>
	  </>
	);
  }
  
  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  { !startOverlap && !results && renderParams() }
	  { startOverlap && !results && !error && renderLoading() }
	  { results && renderResults() }
	  { error && renderError() }
	</div>
	
  );
}

)
