
import { memo, useEffect, useState } from 'react';

import {  ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText, Snackbar } from '@mui/material';

import { serverFetchGeneUse } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function GeneUsePane({ context, repertoire, rkey }) {

  const [includeUnknown, setIncludeUnknown] = useState(window.geneUseDefaultIncludeUnknown);
  const [includeFamilyOnly, setIncludeFamilyOnly] = useState(window.geneUseDefaultIncludeFamilyOnly);
  const [log10Counts, setLog10Counts] = useState(window.geneUseDefaultLog10Counts);

  const [results, setResults] = useState(null);
  const [error,setError] = useState(undefined);

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	const loadResults = async () => {

	  serverFetchGeneUse(context, repertoire.Name) 
		.then(result => {
		  setResults(result);
		  setError(undefined);
		})
		.catch(error => {
		  console.error(error);
		  setResults(undefined);
		  setError('Error retrieving gene use results');
		});
	}

	loadResults();
	
  }, [context, repertoire]);
  
  // +---------+
  // | actions |
  // +---------+

  function toggleUnknownCheckbox() { setIncludeUnknown(!includeUnknown); }
  function toggleFamilyOnlyCheckbox() { setIncludeFamilyOnly(!includeFamilyOnly); }
  function toggleLog10CountsCheckbox() { setLog10Counts(!log10Counts); }

  // +------------------+
  // | translateResults |
  // +------------------+

  // we do this all client side because gene use result sets aren't ever
  // very large and it allows us to be more responsible to parameter changes

  function translateResults() {

	var filtered = results;
	if (!includeUnknown) filtered = filtered.filter(vj => (vj.V !== 'X' && vj.J !== 'X'));
	if (!includeFamilyOnly) filtered = filtered.filter(vj => (!vj.V.endsWith('-X') && !vj.J.endsWith('-X')));

	const v = filtered.map(vj => vj.V);
	const j = filtered.map(vj => vj.J);
	const c = filtered.map(vj => (log10Counts ? Math.log10(vj.Count) : vj.Count));

	return([v,j,c]);
  }

  // +---------------+
  // | renderResults |
  // +---------------+

  function renderResults() {

	const [v,j,c] = translateResults();
	
	return(
	  <div>

		<table>
		  <tr>
			<td><xmp>{JSON.stringify(v, null, 2)}</xmp></td>
			<td><xmp>{JSON.stringify(j, null, 2)}</xmp></td>
			<td><xmp>{JSON.stringify(c, null, 2)}</xmp></td>
		  </tr>
		</table>

        <div className={styles.dialogTxt}>

		  <ListItem disablePadding>
            <ListItemButton rule={undefined} onClick={() => toggleUnknownCheckbox()}>
			  <ListItemIcon sx={{ minWidth: '20px' }}>
                <Checkbox edge='start' checked={includeUnknown} tabIndex={-1} disableRipple
						  inputProps={{ 'aria-labelledby': `${rkey}-full` }}
						  sx={{ padding: '0px' }} />
			  </ListItemIcon>
			  <ListItemText sx={{ margin: '0px' }} id={`${rkey}-full`}
                            primary='Include unknown family' />
            </ListItemButton>
		  </ListItem>

		  <ListItem disablePadding>
            <ListItemButton rule={undefined} onClick={() => toggleFamilyOnlyCheckbox()}>
			  <ListItemIcon sx={{ minWidth: '20px' }}>
                <Checkbox edge='start' checked={includeFamilyOnly} tabIndex={-1} disableRipple
						  inputProps={{ 'aria-labelledby': `${rkey}-full` }}
						  sx={{ padding: '0px' }} />
			  </ListItemIcon>
			  <ListItemText sx={{ margin: '0px' }} id={`${rkey}-full`}
                            primary='Include unknown gene' />
            </ListItemButton>
		  </ListItem>

		  <ListItem disablePadding>
            <ListItemButton rule={undefined} onClick={() => toggleLog10CountsCheckbox()}>
			  <ListItemIcon sx={{ minWidth: '20px' }}>
                <Checkbox edge='start' checked={log10Counts} tabIndex={-1} disableRipple
						  inputProps={{ 'aria-labelledby': `${rkey}-full` }}
						  sx={{ padding: '0px' }} />
			  </ListItemIcon>
			  <ListItemText sx={{ margin: '0px' }} id={`${rkey}-full`}
                            primary='Use Log10 Counts' />
            </ListItemButton>
		  </ListItem>

        </div>
	  </div>
	);
  }

  // +-------------+
  // | renderError |
  // +-------------+

  function renderError() {

	return(
	  <div className={styles.hdr}>{error}</div>
	);
  }
  
  return(

	<div className={styles.container}>
	  
	  { error && renderError() }
	  { results && renderResults() }

	</div>
	
  );
}

)
