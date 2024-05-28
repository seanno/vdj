
import { memo, useState, useEffect } from 'react';
import { FormControl, InputLabel, MenuItem, Select } from '@mui/material';

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchTopX } from './lib/server.js';

import styles from './TopX.module.css'

export default memo(function TopXPane({ context, repertoire, rkey }) {

  const [sort, setSort] = useState(window.topXDefaultSort);
  const [results, setResults] = useState(undefined);
  const [error,setError] = useState(undefined);

  // +-----------+
  // | useEffect |
  // +-----------+

  useEffect(() => {

	const loadResults = async () => {

	  serverFetchTopX(context, repertoire.Name, sort) 
		.then(result => {
		  setResults(result);
		  setError(undefined);
		})
		.catch(error => {
		  console.error(error);
		  setResults(undefined);
		  setError('Error retrieving topX results');
		});
	}

	loadResults();
	
  }, [context, repertoire, sort]);

  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  <div className={styles.select}>
		<FormControl fullWidth>
		  <InputLabel id={`lbl-${rkey}`}>Sort rearrangements by</InputLabel>
		  <Select
			labelId={`lbl-${rkey}`}
			label='Sort rearrangements by'
			value={sort}
			onChange={(evt) => setSort(evt.target.value)} >

			<MenuItem value='Count'>Count</MenuItem>
			<MenuItem value='FractionOfCells'>% Cells</MenuItem>
			<MenuItem value='FractionOfLocus'>% Locus</MenuItem>
		  </Select>
		</FormControl>
	  </div>

	  { results &&
		<RearrangementsTable
		  rearrangements={results.Rearrangements}
		  key={`${rkey}-tbl}`}
		/>
	  }

	  { error &&
		<div className={styles.hdr}>
		  
		</div>
	  }

	</div>
	
  );
}

)
