
import { colorizeRearrangement } from './lib/colorize.jsx';

import styles from './Tables.module.css';

export default function RearrangementsTable({ repertoire, rearrangements, rkey, caption }) {

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

  const useVolume = (repertoire.TotalMilliliters > 0.0);
  
  return(
	<table className={styles.rearrangementsTable}>
	  { caption && <caption>{caption}</caption> }
	  <thead>
		<tr>
		  <th>Locus</th>
		  <th>Count</th>
		  <th>% Locus</th>
		  <th>{ useVolume ? 'Count/ML' : '% Cells'}</th>
		  <th>Rearrangement</th>
		  <th>Amino Acid</th>
		  <th>V Resolved</th>
		  <th>D Resolved</th>
		  <th>J Resolved</th>
		  <th>logProb</th>
		</tr>
	  </thead>
	  <tbody>
		{
		  rearrangements.map((r, irow) => {
			return(
			  <tr key={`${rkey}-rt-${irow}`}>
				<td>{r.Locus}</td>
				<td style={{textAlign: 'right'}}>{r.Count}</td>
				<td style={{textAlign: 'right'}}>{(Math.min(r.FractionOfLocus * 100,100)).toFixed(4)}</td>
				<td style={{textAlign: 'right'}}>{(useVolume ? r.CountPerMilliliter : Math.min(r.FractionOfCells * 100,100)).toFixed(4)}</td>
				<td style={{textAlign: 'right'}}><a href="#" onClick={ (evt) => window.launchIMGT(r.Locus,r.Rearrangement, evt) }>{colorizeRearrangement(r)}</a></td>
				<td>{r.AminoAcid}</td>
				<td>{r.VResolved}</td>
				<td>{r.DResolved}</td>
				<td>{r.JResolved}</td>
				<td style={{textAlign: 'right'}}>{(r.Probability === 0.0 ? '' : r.Probability.toFixed(3))}</td>
			  </tr>
			);
		  })
		}
	  </tbody>
	</table>
  );
}

