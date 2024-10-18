
import { memo, useState, useEffect, Fragment } from 'react';
import { Button } from '@mui/material';
import { Chart } from "react-google-charts";

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchDxOptions, serverFetchTracking } from './lib/server.js';

import styles from './Pane.module.css'
import tableStyles from './Tables.module.css';

export default memo(function TrackingPane({ context, repertoires, addTab, rkey }) {

  const [dxOptions, setDxOptions] = useState(undefined);
  const [startTracking, setStartTracking] = useState(false);
  const [trackingResults, setTrackingResults] = useState(undefined);
  const [error,setError] = useState(undefined);
  const [chartApi,setChartApi] = useState(undefined);

  // +-----------------+
  // | shapes & labels |
  // +-----------------+
  
  const targetShapes = [ 'circle', 'triangle', 'square',
						 'diamond', 'star', 'polygon' ];

  const targetColors = [ '#3366CC',	'#DC3912', '#FF9900', '#109618',
						 '#990099',	'#3B3EAC', '#0099C6', '#DD4477',
						 '#66AA00', '#B82E2E', '#316395', '#994499',
						 '#22AA99', '#AAAA11', '#6633CC', '#E67300',
						 '#8B0707', '#329262', '#5574A6', '#3B3EAC' ];

  function getTargetShape(itarget) {
	return(targetShapes[itarget % targetShapes.length]);
  }

  function getTargetColor(itarget) {
	return(targetColors[itarget % targetColors.length]);
  }

  function getTargetLabel(itarget) {
	return(String.fromCharCode(65 + itarget));
  }

  // +-----------------------------+
  // | cellular / cellfree madness |
  // +-----------------------------+

  function isCellfree(rep) {
	return(rep.TotalMilliliters > 0.0);
  }

  function isMixedMode() {
	var cellular = false;
	var cellfree = false;
	for (var i = 0; i < trackingResults.Repertoires.length; ++i) {
	  const thisIsCellfree = isCellfree(trackingResults.Repertoires[i]);
	  if (thisIsCellfree) cellfree = true;
	  if (!thisIsCellfree) cellular = true;
	}
	return(cellular && cellfree);
  }
  
  function convertValue(inputValue, rep) {
	if (isCellfree(rep)) return(inputValue);
	return(Math.min(inputValue * 100, 100));
  }

  function convertAndFormatValue(inputValue, rep) {
	if (isCellfree(rep)) return(`${Math.round(inputValue).toLocaleString()}/ML`);
	return(`${Math.min(inputValue * 100, 100).toFixed(4)}%`);
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
		  //console.log(JSON.stringify(result, null, 2));
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
  
  // +--------------------+
  // | renderResultsChart |
  // +--------------------+

  function getDataTable(mixedMode) {
	
	const columns = [ 'Repertoire' ];
	for (var i = 0; i < trackingResults.TargetValues.length; ++i) {
	  const lbl = getTargetLabel(i);
	  columns.push(lbl);
	  if (mixedMode) columns.push(lbl + ' (cellfree)');
	}

	const data = [columns];

	const multiplier = (mixedMode ? 2 : 1);
	for (var irep = 0; irep < trackingResults.Repertoires.length; ++irep) {
	  const rep = trackingResults.Repertoires[irep];
	  const row = [ rep.Name ];
	  data.push(row);
	  const offset = (mixedMode && isCellfree(rep) ? 1 : 0);
	  for (var itarget = 0; itarget < trackingResults.TargetValues.length; ++itarget) {
		const tv = trackingResults.TargetValues[itarget];
		if (mixedMode) {
		  row.push(isCellfree(rep) ? undefined : convertValue(tv.Values[irep], rep));
		  row.push(isCellfree(rep) ? convertValue(tv.Values[irep], rep) : undefined);
		}
		else {
		  row.push(convertValue(tv.Values[irep], rep));
		}
	  }
	}

	return(data);
  }

  function renderResultsChart() {
	const mixedMode = isMixedMode();
	
	const options = {
	  pointSize: 10,
	  pointsVisible: true,
	  legend: 'none'
	};
	
	if (mixedMode) {

	  options.vAxes = {
		0: { title: '% Cells' },
		1: { title: 'Count/ML' }
	  };

	  options.series = {};
	  for (var i = 0; i < trackingResults.TargetValues.length; ++i) {
		
		options.series[i*2] = {
		  pointShape: getTargetShape(i),
		  color: getTargetColor(i),
		  targetAxisIndex: 0
		};
		
		options.series[(i*2)+1] = {
		  pointShape: getTargetShape(i),
		  color: getTargetColor(i),
		  targetAxisIndex: 1
		};
	  }
	}
	else {
	  
	  const isCF = isCellfree(trackingResults.Repertoires[0]);
	  options.vAxes = {
		0: { title: (isCF ? 'Count/ML' : '% Cells') }
	  };

	  options.series = {};
	  for (var i = 0; i < trackingResults.TargetValues.length; ++i) {
		options.series[i] = {
		  pointShape: getTargetShape(i),
		  color: getTargetColor(i),
		  targetAxisIndex: 0
		};
	  }
	}
	
	//console.log(JSON.stringify(options));

	return(
	  <>
		<Chart
		  chartType='LineChart'
		  data={getDataTable(mixedMode)}
		  width='700px'
		  height='400px'
		  options={options}
		/>
	  </>
	);
  }

  // +--------------------+
  // | renderResultsTable |
  // +--------------------+

  function openSearch(rearrangement) {

	const newTab = {
	  view: 'search',
	  name: 'Search',
	  context: context,
	  repertoires: repertoires,

	  params: {
		type: 'Rearrangement',
		motif: rearrangement,
		muts: 0,
		full: true,
		start: true
	  }
	};

	addTab(newTab);
  }

  function renderResultsTable() {

	const repertoireHeaders = trackingResults.Repertoires.map((r, irep) => {

	  var dateStr = undefined;
	  if (r.Date) {
		const d = new Date(r.Date.year, r.Date.month - 1, r.Date.day);
		dateStr = d.toLocaleDateString();
	  }

	  return(
		<th className={tableStyles.top} key={`${rkey}-hdr-${irep}`}>
		  {r.Name}
		  {dateStr && <><br/>{dateStr}</> }
		</th>
	  );
	});

	const rows = trackingResults.TargetValues.map((tv, itv) => {

	  const vals = tv.Values.map((val, ival) => {
		return(
		  <td className={tableStyles.right} key={`${rkey}-tvrow-${itv}-${ival}`}>
			{convertAndFormatValue(val, trackingResults.Repertoires[ival])}
		  </td>
		);
	  });

	  const labelStyle = {
		backgroundColor: getTargetColor(itv),
		textAlign: 'center',
		color: 'white',
		fontWeight: 'bold'
	  };

	  const r = tv.Target.Rearrangement;
			
	  return(
	  <tr key={`${rkey}-tvrow-${itv}`}>
		<td style={labelStyle} onClick={() => labelClick(itv)} >
		  {getTargetLabel(itv)}
		</td>
		{vals}
		<td>
		  {tv.Target.Locus}
		</td>
		<td>
		  <a href="#" onClick={() => openSearch(r)}>{r}</a>
		</td>
	  </tr>
	  );
	});

	return(
	  <table style={{ width: 'auto' }} className={tableStyles.rearrangementsTable}>
		<thead>
		  <tr>
			<th className={tableStyles.top}>Label</th>
			{ repertoireHeaders }
			<th className={tableStyles.top}>Locus</th>
			<th className={tableStyles.top}>Rearrangement</th>
		  </tr>
		</thead>
		<tbody>
		  {rows}
		</tbody>
	  </table>
	);
  }

  // +---------------+
  // | renderResults |
  // +---------------+
  
  function renderResults() {
	return(
	  <>
		{renderResultsChart()}
		{renderResultsTable()}
	  </>
	);
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
