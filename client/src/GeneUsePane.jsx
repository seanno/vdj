
import { memo, useEffect, useState } from 'react';

import {  ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText, Snackbar } from '@mui/material';

import { Canvas } from '@react-three/fiber';

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
  // very large and it allows us to be more responsive to parameter changes

  function translateResults() {

	// first collect up / filter values
	
	const countsMap = {};
	const vMap = {};
	const jMap = {};

	var minCount = Number.MAX_VALUE;
	var maxCount = Number.MIN_VALUE;
	
	for (var i = 0; i < results.length; ++i) {

	  const vj = results[i];
	  
	  if (!includeUnknown && (vj.V === 'X' || vj.J === 'X')) continue;
	  if (!includeFamilyOnly && (vj.V.endsWith('-X') || vj.J.endsWith('-X'))) continue;

	  const adjCount = (log10Counts ? Math.log10(vj.Count) : vj.Count);
	  countsMap[vj.V + '|' + vj.J] = adjCount;
	  vMap[vj.V] = true; jMap[vj.J] = true;
	  if (adjCount > maxCount) maxCount = adjCount;
	  if (adjCount < minCount) minCount = adjCount;
	}

	// now get unique V and Js

	const uniqueVs = Object.keys(vMap).sort();
	const uniqueJs = Object.keys(jMap).sort();

	// and return it all. I feel a little badly about this because it's kind
	// of a sloppy abstraction but I can only loop over arrays so many times
	// before it's too much to take.

	return([ uniqueVs, uniqueJs, countsMap, minCount, maxCount ]);
  }

  // +---------------+
  // | renderResults |
  // +---------------+

  function renderChart() {

	const [ vDim, jDim, counts, minCount, maxCount ] = translateResults();

	const barWidth = window.geneUseBarWidth;
	const barGap = window.geneUseBarGap;
	const barMax = window.geneUseBarMax;
	
	// render the bars where there is a value 
	
	const bars = [];
	for (var jindex = 0; jindex < jDim.length; ++jindex) {
	  
	  const j = jDim[jindex];
	  const color = window.geneUseColors[jindex % window.geneUseColors.length];
	  
	  for (var vindex = 0; vindex < vDim.length; ++vindex) {
		
		const v = vDim[vindex];
		var count = counts[v + '|' + j];
		if (!count) count = 0;

		const height = (count / maxCount) * barMax; if (height > 0) console.log(`BIG: ${height}`);
		const boxPosition = [ vindex * (barWidth + barGap), height / 2, jindex * (barWidth + barGap) ];
		const boxGeometry = [ barWidth, height, barWidth ];

		bars.push(
		  <mesh position={ boxPosition }>
			<boxGeometry args={ boxGeometry} />
			<meshPhongMaterial color={color} shininess={20}/>
		  </mesh>
		);
	  }
	}

	const cameraPosition = [
	  (vDim.length * (barWidth + barGap)) * .8,
	  barMax * 1.5,
	  (jDim.length * (barWidth + barGap)) * 5
	];

	const lightPosition = [
	  (vDim.length * (barWidth + barGap)),
	  barMax * 1.5,
	  (jDim.length * (barWidth + barGap))
	];

	const lookAtX = ((vDim.length * (barWidth + barGap)) / 2) * 1.2;
	const lookAtY = barMax / 2.5;
	const lookAtZ = (jDim.length * (barWidth + barGap)) / 2;

	const fov = 40;

	// now the actual chart

	return(
	  <div style={{ height: window.geneUseHeight, width: window.geneUseWidth }}>
		<Canvas
		  camera={{ position: cameraPosition, fov: fov }}
		  onCreated={({camera}) => camera.lookAt(lookAtX, lookAtY, lookAtZ) } >
		  <ambientLight intensity={1} />
		  <directionalLight position={lightPosition} intensity={1.1} />
		  { bars }
		</Canvas>
	  </div>
	);
  }
  
  function renderResults() {

	return(
	  <div>

		{ renderChart() }
		
		{
		  /*		
		<table>
		  <tbody>
			<tr>
			  <td><xmp>{JSON.stringify(v, null, 2)}</xmp></td>
			  <td><xmp>{JSON.stringify(j, null, 2)}</xmp></td>
			  <td><xmp>{JSON.stringify(c, null, 2)}</xmp></td>
			</tr>
		  </tbody>
		</table>
		  */
		}
		
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
