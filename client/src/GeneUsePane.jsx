
import { memo, useEffect, useState } from 'react';

import {  ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText, Snackbar } from '@mui/material';

import { Canvas } from '@react-three/fiber';
import { OrbitControls } from '@react-three/drei';

import { serverFetchGeneUse } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function GeneUsePane({ context, repertoire, rkey }) {

  const [includeUnknown, setIncludeUnknown] = useState(window.geneUseDefaultIncludeUnknown);
  const [includeFamilyOnly, setIncludeFamilyOnly] = useState(window.geneUseDefaultIncludeFamilyOnly);
  const [log10Counts, setLog10Counts] = useState(window.geneUseDefaultLog10Counts);

  const [hoverTip, setHoverTip] = useState(null);
  
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

  async function tipToClip() {

	if (!hoverTip) return;
	
	const queryOpts = { name: "clipboard-write", allowWithoutGesture: "false" };
	const perms = await navigator.permissions.query(queryOpts);

	if (perms.state === "denied") {
	  alert("Access to the clipboard was denied");
	  return;
	}

	navigator.clipboard.writeText(hoverTip.replaceAll("<br/>", "\n"))
	  .catch((err) => alert(err));
  }
  
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
  
  function renderChart(vDim, jDim, counts, minCount, maxCount) {

	const barWidth = (window.geneUseDx / vDim.length) - window.geneUseGap;
	const barGap = window.geneUseGap;
	const barMax = window.geneUseDy;

	console.log(`barWidth=${barWidth} barGap=${barGap} barMax=${barMax}`);

	// render the bars where there is a value 
	
	const bars = [];
	for (var jindex = 0; jindex < jDim.length; ++jindex) {
	  
	  const j = jDim[jindex];
	  const color = window.geneUseColors[jindex % window.geneUseColors.length];
	  
	  for (var vindex = 0; vindex < vDim.length; ++vindex) {
		
		const v = vDim[vindex];
		const key=[v + '|' + j];
		var count = counts[key];
		if (!count) count = 0;

		const height = (count / maxCount) * barMax;
		const boxPosition = [ vindex * (barWidth + barGap), height / 2, jindex * (barWidth + barGap) * -1];
		const boxGeometry = [ barWidth, height, barWidth ];

		const tipText = `${v}<br/>${j}<br/>${count}`;
		
		bars.push(
		  <mesh
			key={ rkey + key }
			onPointerOver={() => setHoverTip(tipText)}
			onPointerOut={() => setHoverTip(null)}
			onClick={tipToClip}
			position={ boxPosition } >

			<boxGeometry args={ boxGeometry} />
			<meshPhongMaterial color={color} shininess={40}/>
		  </mesh>
		);
	  }
	}

	const cameraPosition = [
	  window.geneUseDx * 3 / 4,
	  barMax * 3,
	  350
	];

	const lightPosition = [
	  window.geneUseDx,
	  barMax * 1.5,
	  (jDim.length * (barWidth + barGap))
	];

	const targetPosition = [
	  window.geneUseDx / 2,
	  barMax / 4,
	  (jDim.length * (barWidth + barGap)) / 2 * -1
	];

	const fov = 22;

	// now the actual chart

	return(
	  <div style={{ border: '1px solid #a0a0a0', position: 'relative', height: window.geneUseHeight, width: window.geneUseWidth }}>
		
		{ hoverTip &&
		  <div
			dangerouslySetInnerHTML={{__html: hoverTip}}
			style={{ position: 'absolute', backgroundColor: 'white', padding: '2px', zIndex: 999 }}
		  /> }
		
		<Canvas
		  camera={{ position: cameraPosition, fov: fov }} >

		  <ambientLight intensity={1.8} />
		  <directionalLight position={lightPosition} intensity={1.2} />
		  { bars }
		  <OrbitControls target={targetPosition}/>
		</Canvas>
	  </div>
	);
  }
  
  function renderResults() {

	const [ vDim, jDim, counts, minCount, maxCount ] = translateResults();

	return(
	  <div>

		{ renderChart(vDim, jDim, counts, minCount, maxCount) }

		<div style={{ display: 'grid', gridTemplateColumns: '300px 1fr' }}>
		  
          <div style={{ gridRow: 1, gridColumn: 1 }} className={styles.dialogTxt}>

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

		  <div style={{ gridRow: 1, gridColumn: 2 }}>

			<ul>
			  <li>{vDim.length} unique V Genes</li>
			  <li>{jDim.length} unique J Genes</li>
			  <li>{Object.keys(counts).length} unique combinations</li>
			</ul>
			
		  </div>
		  
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
