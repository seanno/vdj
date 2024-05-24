
import { memo, useState } from 'react';

export default memo(function OvelapPane({ context, repertoires, rkey }) {

  // +--------+
  // | render |
  // +--------+

  return(

	<div>
	  <p>OVERLAP NYI</p>
	  <p>{context} - {JSON.stringify(repertoires)}</p>
	</div>
	
  );
}

)
