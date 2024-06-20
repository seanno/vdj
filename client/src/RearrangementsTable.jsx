
import { colorizeRearrangement } from './lib/colorize.jsx';

import styles from './Tables.module.css';

export default function RearrangementsTable({ rearrangements, rkey, caption }) {

  function renderCaption() {
	if (!caption) return(undefined);
	return(<h2>{caption}</h2>);
  }

  // +------------+
  // | no results |
  // +------------+
  
  if (!rearrangements || rearrangements.length === 0) {
	
	return(
	  <div className={styles.noResults}>
		{ caption && <h1>{caption}</h1> }
		No results
	  </div>
	);
  }

  // +--------------+
  // | normal table |
  // +--------------+

  return(
	<table className={styles.rearrangementsTable}>
	  { caption && <caption>{caption}</caption> }
	  <thead>
		<tr>
		  <th>Locus</th>
		  <th>Count</th>
		  <th>% Locus</th>
		  <th>% Cells</th>
		  <th>Rearrangement</th>
		  <th>Amino Acid</th>
		  <th>V Resolved</th>
		  <th>D Resolved</th>
		  <th>J Resolved</th>
		</tr>
	  </thead>
	  <tbody>
		{
		  rearrangements.map((r, irow) => {
			return(
			  <tr key={`${rkey}-rt-${irow}`}>
				<td>{r.Locus}</td>
				<td style={{textAlign: 'right'}}>{r.Count}</td>
				<td style={{textAlign: 'right'}}>{(r.FractionOfLocus * 100).toFixed(3)}</td>
				<td style={{textAlign: 'right'}}>{(r.FractionOfCells * 100).toFixed(3)}</td>
				<td style={{textAlign: 'right'}}><a href="#" onClick={ (evt) => window.launchIMGT(r.Locus,r.Rearrangement, evt) }>{colorizeRearrangement(r)}</a></td>
				<td>{r.AminoAcid}</td>
				<td>{r.VResolved}</td>
				<td>{r.DResolved}</td>
				<td>{r.JResolved}</td>
			  </tr>
			);
		  })
		}
	  </tbody>
	</table>
  );
}

