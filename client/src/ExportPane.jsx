
import { memo, useState } from 'react';
import { Button, FormControl, FormControlLabel, Radio, RadioGroup } from '@mui/material';

import { exportRepertoire } from './lib/server.js';

import styles from './Pane.module.css'

export default memo(function ExportPane({ context, repertoire, rkey }) {

  const [format, setFormat] = useState(window.exportDefaultFormat);

  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>
	  
	  <div className={styles.hdr}>
		Export repertoire: { repertoire.Name }
	  </div>
	  
	  <div className={styles.dialogTxt}>
		<FormControl>
		  <RadioGroup
			row
			value={format}
			onChange={(evt) => setFormat(evt.target.value)} >
			<FormControlLabel value='Original' control={<Radio/>} label='TSV' />
			<FormControlLabel value='FastaIndex' control={<Radio/>} label='FASTA (by Row)' />
			<FormControlLabel value='FastaHash' control={<Radio/>} label ='FASTA (by Hash)' />
		  </RadioGroup>
		</FormControl>
	  </div>

	  <Button
		variant='outlined'
		onClick={() => exportRepertoire(context, repertoire.Name, format) } >
		Export
	  </Button>

	</div>
	
  );
}

)
