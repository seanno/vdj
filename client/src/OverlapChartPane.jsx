
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

  const chartEvents = [
	{
	  eventName: 'select',
	  callback: ({chartWrapper}) => {
		const chart = chartWrapper.getChart();
		const selection = chart.getSelection();
		if (selection.length === 1) {
		  const item = results.Items[selection[0].row];

		  const newDeets =
				`${results.Repertoires[0].Name}: ${item.Counts[0]}<br/>` +
				`${results.Repertoires[1].Name}: ${item.Counts[1]}<br/>` +
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
	  { type: 'number', r2 },
	  { type: 'string', role: 'tooltip' }
	]];

	var maxVal = 0.0;
	
	for (const i in results.Items) {

	  const item = results.Items[i];
	  
	  const val1 = Math.log10(item.Counts[0]);
	  if (val1 > maxVal) maxVal = val1;
	  
	  const val2 = Math.log10(item.Counts[1]);
	  if (val2 > maxVal) maxVal = val2;

	  const tip = `${item.Key} (${val1},${val2})`;

	  data.push([ val1, val2, tip]);
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
	  <div>
		<div style={{ float: 'left' }}>
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
		  style={{ padding: '20px',
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
