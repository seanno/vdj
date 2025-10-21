
import { memo, useEffect, useState, useRef } from 'react';

import {  Button, ListItem, ListItemButton, ListItemIcon,
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

  // these two are in the same order! also note we keep state for all
  // possible loci --- but we'll only show the ones that are actually
  // in the data set. A bit confusing but easier for state management
  const loci = [ "IGH", "IGK", "IGL", "TCRB", "TCRG", "TCRAD" ];
  const [lociEnabled, setLociEnabled] = useState([ true, true, true, true, true, true]);
  
  const [results, setResults] = useState(null);
  const [error,setError] = useState(undefined);

  const canvasRef = useRef();

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

  function exportImage() {

	const canvas = canvasRef.current;
	if (!canvas) return;

	const link = document.createElement("a");
	link.download = repertoire.Name.replace(/[^a-z0-9-.]/gi, '_') + " - Gene Use.png";
	link.href = canvas.toDataURL("image/png");
	link.click();
  }
  
  // +---------------+
  // | locus helpers |
  // +---------------+

  function findLocus(input) {
	if (input == null) return(-1);
	for (var i = 0; i < loci.length; ++i) {
	  if (input.startsWith(loci[i])) return(i);
	}
	
	return(-1);
  }
  
  function getLocus(input) {
	const i = findLocus(input);
	return(i === -1 ? null : loci[i]);
  }

  function locusMismatch(v, j) {
	return(findLocus(v) != findLocus(j));
  }

  function locusEnabled(locus) {
	const i = findLocus(locus);
	return(i === -1 ? true : lociEnabled[i]);
  }

  function toggleLocusEnabled(ilocus) {
	// clone to trigger state updates
	const newLociEnabled = [...lociEnabled];
	newLociEnabled[ilocus] = !newLociEnabled[ilocus];
	setLociEnabled(newLociEnabled);
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
	const locusMap = {};

	var minCount = Number.MAX_VALUE;
	var maxCount = Number.MIN_VALUE;

	for (var i = 0; i < results.length; ++i) {

	  const vj = results[i];

	  // respect unknown checkboxes
	  if (!includeUnknown && (vj.V === 'X' || vj.J === 'X')) continue;
	  if (!includeFamilyOnly && (vj.V.endsWith('-X') || vj.J.endsWith('-X'))) continue;

	  // now respect locus checkboxes. Note that we still add to the map even
	  // if they are unselected, because we want the checkboxes to display
	  const vLocus = getLocus(vj.V); if (vLocus) locusMap[vLocus] = true;
	  const jLocus = getLocus(vj.J); if (jLocus) locusMap[jLocus] = true;
	  if (!locusEnabled(vLocus) || !locusEnabled(jLocus)) continue;

	  // OK, add it!
	  const adjCount = (log10Counts ? Math.log10(vj.Count) : vj.Count);
	  if (adjCount > maxCount) maxCount = adjCount;
	  if (adjCount < minCount) minCount = adjCount;
	  
	  vMap[vj.V] = true;
	  jMap[vj.J] = true;
	  
	  countsMap[vj.V + '|' + vj.J] = {
		count: adjCount,
		uniques: vj.Uniques,
	  };
	}

	// now get unique V and Js

	const uniqueVs = Object.keys(vMap).sort(); 
	const uniqueJs = Object.keys(jMap).sort();

	// and return it all. I feel a little badly about this because it's kind
	// of a sloppy abstraction but I can only loop over arrays so many times
	// before it's too much to take.

	return([ uniqueVs, uniqueJs, countsMap, locusMap, minCount, maxCount ]);
  }

  // +---------------+
  // | renderResults |
  // +---------------+

  function renderChart(vDim, jDim, counts, minCount, maxCount) {

	if (vDim.length === 0 && jDim.length === 0) return;
	
	const barWidth = (window.geneUseDx / vDim.length) - window.geneUseGap;
	const barGap = window.geneUseGap;
	const barMax = window.geneUseDy;

	// render the bars where there is a value 
	
	const bars = [];
	for (var jindex = 0; jindex < jDim.length; ++jindex) {
	  
	  const j = jDim[jindex];
	  const color = window.geneUseColors[jindex % window.geneUseColors.length];
	  
	  for (var vindex = 0; vindex < vDim.length; ++vindex) {
		
		const v = vDim[vindex];
		const key=[v + '|' + j];

		const countInfo = counts[key];
		if (!countInfo && locusMismatch(v,j)) continue;

		const count = countInfo ? countInfo.count : 0;
		const uniques = countInfo ? countInfo.uniques : 0;

		const height = (count / maxCount) * barMax;
		const boxPosition = [ vindex * (barWidth + barGap), height / 2, jindex * (barWidth + barGap) * -1];
		const boxGeometry = [ barWidth, height, barWidth ];

		const tipText = `${v} / ${j}<br/>Count: ${count}<br/>Unique: ${uniques}`;
		
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
		  ref={ canvasRef }
		  gl={{ preserveDrawingBuffer: true }}
		  camera={{ position: cameraPosition, fov: fov }} >

		  <ambientLight intensity={1.8} />
		  <directionalLight position={lightPosition} intensity={1.2} />
		  { bars }
		  <OrbitControls target={targetPosition}/>
		</Canvas>
	  </div>
	);
  }

  function renderLociCheckboxes(locusMap) {

	const lociCheckboxes = [];
	
	for (let i = 0; i < loci.length; ++i) {

	  const locus = loci[i];
	  if (!locusMap[locus]) continue;

	  lociCheckboxes.push(
		<ListItem disablePadding key={`${rkey}-${locus}`}>
          <ListItemButton rule={undefined} onClick={() => toggleLocusEnabled(i)}>
			<ListItemIcon sx={{ minWidth: '20px' }}>
              <Checkbox edge='start' checked={lociEnabled[i]} tabIndex={-1} disableRipple
						inputProps={{ 'aria-labelledby': `${rkey}-chk-${locus}` }}
						sx={{ padding: '0px' }} />
			</ListItemIcon>
			<ListItemText sx={{ margin: '0px' }} id={`${rkey}-chk-${locus}`}
                          primary={locus} />
          </ListItemButton>
		</ListItem>
	  );
	}

	return(lociCheckboxes);
  }
  
  function renderResults() {

	const [ vDim, jDim, counts, locusMap, minCount, maxCount ] = translateResults();

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

			<Button
			  variant='outlined'
			  sx={{ mt: 1 }}
			  onClick={ exportImage } >
			  Export
			</Button>

          </div>

		  <div style={{ gridRow: 1, gridColumn: 2 }}>

			{ renderLociCheckboxes(locusMap) }
			
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
