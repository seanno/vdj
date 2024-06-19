
import { Fragment } from 'react';
import styles from './RepertoireHeader.module.css'

export default function RepertoireHeader({ repertoire, rkey }) {

  const locusDivs = Object.keys(repertoire.LocusCounts).map((locus, irow) => {
	return(
	  <Fragment key={`${rkey}-hdr-${irow}`}>
		<div className={styles.label} style={{ gridRow: irow+2, gridColumn: 1 }}>{locus}:</div>
		<div style={{ gridRow: irow+2, gridColumn: 2 }}>{repertoire.LocusCounts[locus].toLocaleString()}</div>
	  </Fragment>
	);
  });
	
  return(

	<div className={styles.container}>
	  
	  <div className={styles.label} style={{ gridRow: 1, gridColumn: 3 }}>Uniques:</div>
	  <div style={{ gridRow: 1, gridColumn: 4 }}>{repertoire.TotalUniques.toLocaleString()}</div>

	  { repertoire.TotalCells > 0 &&
		<>
		  <div className={styles.label} style={{ gridRow: 2, gridColumn: 3 }}>Cells:</div>
		  <div style={{ gridRow: 2, gridColumn: 4 }}>{repertoire.TotalCells.toLocaleString()}</div>
		</>
	  }
		   
	  <div className={styles.label} style={{ gridRow: 1, gridColumn: 1 }}>Count:</div>
	  <div style={{ gridRow: 1, gridColumn: 2 }}>{repertoire.TotalCount.toLocaleString()}</div>

	  {locusDivs}

	</div>
	
  );
}

