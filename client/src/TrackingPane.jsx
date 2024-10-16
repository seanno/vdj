
import { memo, useState, useEffect } from 'react';

import RearrangementsTable from './RearrangementsTable.jsx';
import { serverFetchDxOptions } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function TrackingPane({ context, repertoires, rkey }) {

  const [dxOptions, setDxOptions] = useState(undefined);
  const [error,setError] = useState(undefined);

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

  // +---------------+
  // | renderOptions |
  // +---------------+

  function renderOptions() {

	const tablesJsx = dxOptions.map((rrs,irep) => {
	  return(
		<RearrangementsTable
		  repertoire={rrs.Repertoire}
		  rearrangements={rrs.Rearrangements}
		  caption={rrs.Repertoire.Name}
		  key={`${rkey}-tbl-${irep}}`}
		/>
	  );
	});
	
	return(
	  <>
		{tablesJsx}
	  </>
	);
  }
  
  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>

	  { dxOptions && renderOptions() }
	  { error && <div className={styles.hdr}>{error}</div> }

	</div>
  );
}

)
