
import { List, ListItem, ListItemButton, ListItemIcon,
		 Checkbox, ListItemText } from '@mui/material';

export default function RepertoirePicker({ repertoireList,
										   selectedRepertoires,
										   changeSelection }) {

  // +---------+
  // | actions |
  // +---------+
  
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

	changeSelection(newRepertoires);
  }

  function listOnKeyDown(evt) {
	if (evt.key === 'a' && evt.ctrlKey) {
	  evt.preventDefault();
	  changeSelection([...repertoireList]);
	}
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
  
  // +--------+
  // | Render |
  // +--------+

  return(

    <div>

	  <List
		onKeyDown={ (evt) => listOnKeyDown(evt) }
		sx={{ width: '100%',
			  overflow: 'auto',
			  maxHeight: '300px',
			  mt: 1, mb: 2 }}>

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

	</div>
	
  );
}

