
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

  // +-------------+
  // | chartEvents |
  // +-------------+

  function formatFraction(rep, count) {
	
	const useVolume = (rep.TotalMilliliters > 0.0);

	const val = (useVolume
				 ? count / rep.TotalMilliliters
				 : (rep.TotalCells > 0 ? count / rep.TotalCells * 100 : 0.0));

	return(val.toFixed(3) + (useVolume ? ' Count/ML' : ' % Cells'));
  }
  
  const chartEvents = [
	{
	  eventName: 'select',
	  callback: ({chartWrapper}) => {
		const chart = chartWrapper.getChart();
		const selection = chart.getSelection();
		
		if (selection.length === 1) {
		  
		  const item = results.Items[selection[0].row];

		  const rep0 = results.Repertoires[0];
		  const count0 = item.Counts[0];
		  const valTxt0 = formatFraction(rep0, count0);

		  const rep1 = results.Repertoires[1];
		  const count1 = item.Counts[1];
		  const valTxt1 = formatFraction(rep1, count1);

		  const newDeets =
				`${rep0.Name}: ${count0} (${valTxt0})<br/>` +
				`${rep1.Name}: ${count1} (${valTxt1})<br/>` +
				'<br/>' +
				item.Key.replaceAll(', ', '<br/>').replaceAll('...', '<br/>...');
				
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

	const r1 = results.Repertoires[0].Name;
	const r2 = results.Repertoires[1].Name;

	const data = [[
	  { type: 'number', r1 },
	  { type: 'number', r2 }
	]];

	var maxVal = 0.0;
	
	for (const i in results.Items) {

	  const item = results.Items[i];
	  
	  const val1 = Math.log10(item.Counts[0]);
	  if (val1 > maxVal) maxVal = val1;
	  
	  const val2 = Math.log10(item.Counts[1]);
	  if (val2 > maxVal) maxVal = val2;

	  data.push([ val1, val2 ]);
	}

	maxVal = Math.floor(maxVal + 1);
	const ticks = [];
	for (var i = 0; i <= maxVal; ++i) { ticks.push(i) }
	
	const options = {
	  hAxis: {
		title: r1,
		ticks: ticks
	  },
	  vAxis: {
		title: r2,
		ticks: ticks
	  },
	  legend: 'none',
	  chartArea: { top: 20, width: 380, height: 380 }
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
