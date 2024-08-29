
import { memo, useState, useEffect } from 'react';
import { Chart } from "react-google-charts";

import { serverFetchOverlap } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function OverlapChartPane({ context, repertoires, params, rkey }) {

  const [results, setResults] = useState(undefined);
  const [error,setError] = useState(undefined);

  const [details, setDetails] = useState('');

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	if (results || error) return;
	
	const loadResults = async () => {

	  serverFetchOverlap(context, repertoires, params.overlapType, 'Combined')
		.then(result => {
		  setResults(result);
		})
		.catch(error => {
		  console.error(error);
		  setError('Error overlapping repertoires');
		});
	}

	loadResults();
	
  }, []);

  // +---------------+
  // | Normalization |
  // +---------------+

  function getNormLabel(rep) {
	if (rep.TotalMilliliters > 0.0) return("per ML");
	if (rep.TotalCells > 0) return("% Cells");
	return("Count");
  }

  function getNormValue(rep, count) {
	if (rep.TotalMilliliters > 0.0) return(count / rep.TotalMilliliters);
	if (rep.TotalCells > 0) return(Math.min(count / rep.TotalCells * 1000000, 1000000));
	return(count);
  }

  function getNormString(rep, count) {
	const countStr = count.toLocaleString();
	const val = getNormValue(rep, count);
	if (rep.TotalMilliliters > 0.0) return(`${countStr} (${Math.round(val).toLocaleString()} per ML)`);
	if (rep.TotalCells > 0) return(`${countStr} (${(val/10000).toFixed(4)}% of Cells)}`);
	return(countStr);
	
  }

  // +-------------+
  // | chartEvents |
  // +-------------+

  const chartEvents = [
	{
	  eventName: 'select',
	  callback: ({chartWrapper}) => {
		const chart = chartWrapper.getChart();
		const selection = chart.getSelection();
		
		if (selection.length === 1) {
		  
		  const item = results.Items[selection[0].row];

		  const rep0 = results.Repertoires[0];
		  const txt0 = getNormString(rep0, item.Counts[0]);
			
		  const rep1 = results.Repertoires[1];
		  const txt1 = getNormString(rep1, item.Counts[1]);

		  const key = item.Key.replaceAll(', ', '<br/>').replaceAll('...', '<br/>...');

		  const newDeets =
				`${rep0.Name}: ${txt0}<br/>` +
				`${rep1.Name}: ${txt1}<br/>` +
				'<br/>' + key +
				(key.endsWith("...") ? ` (${item.KeyCount} total sequences)` : '');
				
		  setDetails(newDeets);
		}
	  }
	}
  ];

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

	const rep0 = results.Repertoires[0];
	const rep1 = results.Repertoires[1];
	
	const title0 = rep0.Name + ' (' + getNormLabel(rep0) + ')';
	const title1 = rep1.Name + ' (' + getNormLabel(rep1) + ')';

	const data = [[
	  { type: 'number', title0 },
	  { type: 'number', title1 }
	]];

	var maxVal = 0.0;
	
	for (const i in results.Items) {

	  const item = results.Items[i];

	  const count0 = item.Counts[0];
	  const val0 = (count0 === 0 ? -0.25 : Math.max(Math.log10(getNormValue(rep0, count0)), 0));
	  if (val0 > maxVal) maxVal = val0;
	  
	  const count1 = item.Counts[1];
	  const val1 = (count1 === 0 ? -0.25 : Math.max(Math.log10(getNormValue(rep1, count1)), 0));
	  if (val1 > maxVal) maxVal = val1;

	  data.push([ val0, val1 ]);
	}

	maxVal = Math.floor(maxVal + 1);
	const ticks = [];
	for (var i = 0; i <= maxVal; ++i) { ticks.push(i) }
	
	const options = {
	  hAxis: {
		title: title0,
		ticks: ticks,
		viewWindow: { min: -0.25, max: maxVal }
	  },
	  vAxis: {
		title: title1,
		ticks: ticks,
		viewWindow: { min: -0.25, max: maxVal }
	  },
	  legend: 'none',
	  chartArea: { right:20, top: 20, width: 380, height: 380 }
	};

	return(
	  <div style={{ whiteSpace: 'nowrap' }} >
		<div style={{ display: 'inline-block', verticalAlign: 'top' }}>
		  <Chart
			chartType='ScatterChart'
			data={data}
			width='450px'
			height='450px'
			options={options}
			chartEvents={chartEvents}
		  />
		</div>

		<div 
		  dangerouslySetInnerHTML={{ __html: details }}
		  style={{ display: 'inline-block',
				   verticalAlign: 'top',
				   padding: '20px',
				   whiteSpace: 'nowrap',
				   fontFamily: 'Courier New',
				   fontSize: 'small' }}>
		</div>
		
	  </div>
	);
  }
  
  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  { !results && !error & renderLoading() }
	  { results && renderResults() }
	  { error && renderError() }
	</div>
	
  );
}

)
