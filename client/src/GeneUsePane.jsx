
import { memo, useEffect, useState } from 'react';

import {  ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText, Snackbar } from '@mui/material';

import styles from './Pane.module.css'

export default memo(function GeneUsePane({ context, repertoire, rkey }) {

  const [format, setFormat] = useState(window.exportDefaultFormat);

  const [includeUnknown, setIncludeUnknown] = useState(window.geneUseDefaultIncludeUnknown);
  const [includeFamilyOnly, setIncludeFamilyOnly] = useState(window.geneUseDefaultIncludeFamilyOnly);
  const [log10Counts, setLog10Counts] = useState(window.geneUseDefaultLog10Counts);

  // +-----------+
  // | useEffect |
  // +-----------+

  /*
  useEffect(() => {
	// nyi
  });
  */
  
  // +---------+
  // | actions |
  // +---------+

  function toggleUnknownCheckbox() { setIncludeUnknown(!includeUnknown); }
  function toggleFamilyOnlyCheckbox() { setIncludeFamilyOnly(!includeFamilyOnly); }
  function toggleLog10CountsCheckbox() { setLog10Counts(!log10Counts); }

  // +--------+
  // | render |
  // +--------+

  return(

	<div className={styles.container}>
	  
	  <div className={styles.dialogTxt}>

		<ListItem disablePadding>
		  <ListItemButton rule={undefined} onClick={() => toggleUnknownCheckbox()}>
			<ListItemIcon sx={{ minWidth: '20px' }}>
			  <Checkbox edge='start' checked={includeUnknown} tabIndex={-1} disableRipple
						inputProps={{ 'aria-labelledby': `${rkey}-full` }}
						sx={{ padding: '0px' }} />
			</ListItemIcon>
			<ListItemText sx={{ margin: '0px' }} id={`${rkey}-full`}
						  primary='Include unknown family' />
		  </ListItemButton>
		</ListItem>

		<ListItem disablePadding>
		  <ListItemButton rule={undefined} onClick={() => toggleFamilyOnlyCheckbox()}>
			<ListItemIcon sx={{ minWidth: '20px' }}>
			  <Checkbox edge='start' checked={includeFamilyOnly} tabIndex={-1} disableRipple
						inputProps={{ 'aria-labelledby': `${rkey}-full` }}
						sx={{ padding: '0px' }} />
			</ListItemIcon>
			<ListItemText sx={{ margin: '0px' }} id={`${rkey}-full`}
						  primary='Include unknown gene' />
		  </ListItemButton>
		</ListItem>

		<ListItem disablePadding>
		  <ListItemButton rule={undefined} onClick={() => toggleLog10CountsCheckbox()}>
			<ListItemIcon sx={{ minWidth: '20px' }}>
			  <Checkbox edge='start' checked={log10Counts} tabIndex={-1} disableRipple
						inputProps={{ 'aria-labelledby': `${rkey}-full` }}
						sx={{ padding: '0px' }} />
			</ListItemIcon>
			<ListItemText sx={{ margin: '0px' }} id={`${rkey}-full`}
						  primary='Use Log10 Counts' />
		  </ListItemButton>
		</ListItem>
		
	  </div>

	</div>
	
  );
}

)
