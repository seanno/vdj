
// +------------------+
// | cleanContextName |
// +------------------+

export function cleanContextName(input) {
  return(input ? input.replace(/[\/\\]/g, '-') : input);
}

