
import { useState, useEffect } from 'react';
import { serverFetchRepertoires } from './lib/server.js';

import { List, ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText } from '@mui/material';

export default function RepertoirePicker({ selectedContext,
										   selectedRepertoires,
										   onRepertoiresChange,
										   refreshCounter,
										   showError }) {

  const [repertoireList,setRepertoireList] = useState(undefined);

  function toggleCheckbox(name) {

	const idxSel = findRepertoireIndex(selectedRepertoires, name);
	const newRepertoires = [...selectedRepertoires];

	if (idxSel === -1) {
	  const idxList = findRepertoireIndex(repertoireList, name);
	  newRepertoires.push(repertoireList[idxList]);
	}
	else {
	  newRepertoires.splice(idxSel, 1);
	}

	onRepertoiresChange(newRepertoires);
  }

  function isChecked(name) {
	return(findRepertoireIndex(selectedRepertoires, name) !== -1);
  }

  function findRepertoireIndex(reps, name) {
	for (var i = 0; i < reps.length; ++i) {
	  if (reps[i].Name === name) return(i);
	}
	return(-1);
  }
  
  // +-----------+
  // | useEffect |
  // +-----------+

  // initial load of user contexts
  
  useEffect(() => {

	if (!selectedContext) {
	  setRepertoireList(undefined);
	  return;
	}

	const loadRepertoires = async () => {
	  serverFetchRepertoires(selectedContext)
		.then(result => {
		  setRepertoireList(result);
		  onRepertoiresChange([]);
		})
		.catch(error => {
		  console.error(error);
		  showError('Error loading user contexts');
		});
	}

	loadRepertoires();
	
  }, [selectedContext, refreshCounter]);

  // +--------+
  // | Render |
  // +--------+

  return(

    <div>

	  { selectedContext && !repertoireList &&
		<p>Loading Repertoires...</p>
	  }
	  
	  { selectedContext && repertoireList &&
		<List
		  sx={{ width: '100%' }}>

		  {
			repertoireList.map((r) => {

			  const labelId = `checkbox-list-label-${r.Name}`;
			  
			  return(
				  <ListItem
					key={r.Name}
					disablePadding>

					<ListItemButton
					  rule={undefined}
					  onClick={() => toggleCheckbox(r.Name)}>

					  <ListItemIcon
						sx={{ minWidth: '20px' }}>
						
						<Checkbox
						  edge='start'
						  checked={isChecked(r.Name)}
						  tabIndex={-1}
						  disableRipple
						  inputProps={{ 'aria-labelledby': labelId }}
						  sx={{ padding: '0px' }}
						/>
					  </ListItemIcon>

					  <ListItemText
						sx={{ margin: '0px' }}
						id={labelId}
						primary={r.Name}
					  />
						
					</ListItemButton>
				  </ListItem>
			  );
			})
		  }

		</List>
	  }

	</div>
	
  );
}

