
import { useState } from 'react';
import { Checkbox } from '@mui/material';

import { colorizeRearrangement } from './lib/colorize.jsx';

import styles from './Tables.module.css';

export default function RearrangementsTable({ repertoire, rearrangements, rkey, caption,
											  initialSelections = [], bubbleSelections }) {

  const [selections,setSelections] = useState(initialSelections);

  // +------------+
  // | selections |
  // +------------+

  function isInteractive() {
	return(bubbleSelections);
  }
  
  function isSelected(irow) {
	return(findSelectionIndex(irow) !== -1);
  }

  function toggleCheckbox(irow) {
	
	const i = findSelectionIndex(irow);
	const newSelections = [...selections];

	if (i === -1) newSelections.push(irow);
	else newSelections.splice(i, 1);

	setSelections(newSelections);
	bubbleSelections(repertoire, newSelections);
  }
  
  function findSelectionIndex(irow) {
	for (var i = 0; i < selections.length; ++i) {
	  if (selections[i] == irow) return(i); 
	}
	return(-1);
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

  const useVolume = (repertoire.TotalMilliliters > 0.0);
  
  return(
	<table className={styles.rearrangementsTable}>
	  { caption && <caption>{caption}</caption> }
	  <thead>
		<tr>
		  { isInteractive() && <th>&nbsp;</th> }
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
		  <th>J Index</th>
		  <th>&nbsp;</th>
		</tr>
	  </thead>
	  <tbody>
		{
		  rearrangements.map((r, irow) => {
			return(
			  <tr key={`${rkey}-rt-${irow}`}>

				{ isInteractive() &&
				  <td>
					<Checkbox
					  onClick={() => toggleCheckbox(irow)}
					  checked={isSelected(irow)}
					  tabIndex={-1}
					  disableRipple
					  sx={{ padding: '1px' }}
					/>
				  </td>
				}
				
				<td>{r.Locus}</td>
				<td style={{textAlign: 'right'}}>{r.Count}</td>
				<td style={{textAlign: 'right'}}>{(Math.min(r.FractionOfLocus * 100,100)).toFixed(4)}</td>
				<td style={{textAlign: 'right'}}>{(useVolume ? r.CountPerMilliliter : Math.min(r.FractionOfCells * 100,100)).toFixed(4)}</td>
				<td style={{textAlign: 'right'}}>{colorizeRearrangement(r)}</td>
				<td>{r.AminoAcid}</td>
				<td>{r.VResolved}</td>
				<td>{r.DResolved}</td>
				<td>{r.JResolved}</td>
				<td style={{textAlign: 'right'}}>{(r.Probability === 0.0 ? '' : r.Probability.toFixed(3))}</td>
				<td>{r.JIndex}</td>
				<td><a href="#" onClick={ (evt) => window.launchIMGT(r.Locus,r.Rearrangement, evt) }>imgt</a></td>
			  </tr>
			);
		  })
		}
	  </tbody>
	</table>
  );
}

