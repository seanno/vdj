
export default function IMGTLauncher() {

  window.launchIMGT = (vdjLocus, sequence, evt) => {

	if (evt) evt.preventDefault();
	
	var locusType;
	
	switch (vdjLocus) {
	  case "TCRAD": locusType = "TR"; break;
	  case "TCRB": locusType = "TRB"; break;
	  case "TCRG": locusType = "TRG"; break;
	  case "IGH": locusType = "IGH"; break;
	  case "DJ": locusType = "IGH"; break;
	  case "IGK": locusType = "IGK"; break;
	  case "IGL": locusType = "IGL"; break;
	}

	document.getElementById("imgtLocus").value = locusType;
	document.getElementById("imgtFASTA").value = ">vdj\n" + sequence;
	document.getElementById("imgtForm").submit();
  }

  return(
	<div style={{display: 'none', visibility: 'hidden' }}>
	  
	  <form
		id="imgtForm"
		encType="multipart/form-data"
		action="https://www.imgt.org/IMGT_vquest/analysis"
		method="POST"
		target="_blank">
		
		<input id="imgtLocus" type="hidden" value="IGH" name="receptorOrLocusType" />
		<input id="imgtFASTA" type="hidden" value="XXXXX" name="sequences" />
		
		<input type="hidden" value="human" name="species" />
		<input type="hidden" value="inline" name="inputType" />
		<input type="hidden" value="detailed" name="resultType" />
		<input type="hidden" value="html" name="outputType" />
		<input type="hidden" value="60" name="nbNtPerLine" />
		<input type="hidden" value="60" name="dv_nbNtPerLine" />
		<input type="hidden" value="5" name="dv_nbAlignedSequence" />
		<input type="hidden" value="true" name="dv_V_GENEalignment" />
		<input type="hidden" value="true" name="__checkbox_dv_V_GENEalignment" />
		<input type="hidden" value="true" name="__checkbox_dv_D_GENEalignment" />
		<input type="hidden" value="true" name="dv_J_GENEalignment" />
		<input type="hidden" value="true" name="__checkbox_dv_J_GENEalignment" />
		<input type="hidden" value="true" name="dv_IMGTjctaResults" />
		<input type="hidden" value="true" name="__checkbox_dv_IMGTjctaResults" />
		<input type="hidden" value="false" name="dv_eligibleD_GENE" />
		<input type="hidden" value="true" name="dv_JUNCTIONseq" />
		<input type="hidden" value="true" name="__checkbox_dv_JUNCTIONseq" />
		<input type="hidden" value="true" name="dv_V_REGIONalignment" />
		<input type="hidden" value="true" name="__checkbox_dv_V_REGIONalignment" />
		<input type="hidden" value="true" name="dv_V_REGIONtranlation" />
		<input type="hidden" value="true" name="__checkbox_dv_V_REGIONtranlation" />
		<input type="hidden" value="true" name="dv_V_REGIONprotdisplay" />
		<input type="hidden" value="true" name="__checkbox_dv_V_REGIONprotdisplay" />
		<input type="hidden" value="true" name="dv_V_REGIONmuttable" />
		<input type="hidden" value="true" name="__checkbox_dv_V_REGIONmuttable" />
		<input type="hidden" value="true" name="dv_V_REGIONmutstats" />
		<input type="hidden" value="true" name="__checkbox_dv_V_REGIONmutstats" />
		<input type="hidden" value="true" name="dv_V_REGIONhotspots" />
		<input type="hidden" value="true" name="__checkbox_dv_V_REGIONhotspots" />
		<input type="hidden" value="true" name="dv_IMGTgappedVDJseq" />
		<input type="hidden" value="true" name="__checkbox_dv_IMGTgappedVDJseq" />
		<input type="hidden" value="true" name="dv_IMGTAutomat" />
		<input type="hidden" value="true" name="__checkbox_dv_IMGTAutomat" />
		<input type="hidden" value="true" name="dv_IMGTCollierdePerles" />
		<input type="hidden" value="true" name="__checkbox_dv_IMGTCollierdePerles" />
		<input type="hidden" value="0" name="dv_IMGTCollierdePerlesType" />
		<input type="hidden" value="true" name="sv_V_GENEalignment" />
		<input type="hidden" value="true" name="__checkbox_sv_V_GENEalignment" />
		<input type="hidden" value="true" name="sv_V_REGIONalignment" />
		<input type="hidden" value="true" name="__checkbox_sv_V_REGIONalignment" />
		<input type="hidden" value="true" name="sv_V_REGIONtranslation" />
		<input type="hidden" value="true" name="__checkbox_sv_V_REGIONtranslation" />
		<input type="hidden" value="true" name="sv_V_REGIONprotdisplay" />
		<input type="hidden" value="true" name="__checkbox_sv_V_REGIONprotdisplay" />
		<input type="hidden" value="true" name="sv_V_REGIONprotdisplay2" />
		<input type="hidden" value="true" name="__checkbox_sv_V_REGIONprotdisplay2" />
		<input type="hidden" value="true" name="sv_V_REGIONprotdisplay3" />
		<input type="hidden" value="true" name="__checkbox_sv_V_REGIONprotdisplay3" />
		<input type="hidden" value="true" name="sv_V_REGIONfrequentAA" />
		<input type="hidden" value="true" name="__checkbox_sv_V_REGIONfrequentAA" />
		<input type="hidden" value="true" name="sv_IMGTjctaResults" />
		<input type="hidden" value="true" name="__checkbox_sv_IMGTjctaResults" />
		<input type="hidden" value="true" name="xv_summary" />
		<input type="hidden" value="true" name="__checkbox_xv_summary" />
		<input type="hidden" value="true" name="xv_IMGTgappedNt" />
		<input type="hidden" value="true" name="__checkbox_xv_IMGTgappedNt" />
		<input type="hidden" value="true" name="xv_ntseq" />
		<input type="hidden" value="true" name="__checkbox_xv_ntseq" />
		<input type="hidden" value="true" name="xv_IMGTgappedAA" />
		<input type="hidden" value="true" name="__checkbox_xv_IMGTgappedAA" />
		<input type="hidden" value="true" name="xv_AAseq" />
		<input type="hidden" value="true" name="__checkbox_xv_AAseq" />
		<input type="hidden" value="true" name="xv_JUNCTION" />
		<input type="hidden" value="true" name="__checkbox_xv_JUNCTION" />
		<input type="hidden" value="true" name="xv_V_REGIONmuttable" />
		<input type="hidden" value="true" name="__checkbox_xv_V_REGIONmuttable" />
		<input type="hidden" value="true" name="xv_V_REGIONmutstatsNt" />
		<input type="hidden" value="true" name="__checkbox_xv_V_REGIONmutstatsNt" />
		<input type="hidden" value="true" name="xv_V_REGIONmutstatsAA" />
		<input type="hidden" value="true" name="__checkbox_xv_V_REGIONmutstatsAA" />
		<input type="hidden" value="true" name="xv_V_REGIONhotspots" />
		<input type="hidden" value="true" name="__checkbox_xv_V_REGIONhotspots" />
		<input type="hidden" value="true" name="xv_parameters" />
		<input type="hidden" value="true" name="__checkbox_xv_parameters" />
		<input type="hidden" value="true" name="__checkbox_xv_scFv" />
		<input type="hidden" value="1" name="IMGTrefdirSet" />
		<input type="hidden" value="true" name="IMGTrefdirAlleles" />
		<input type="hidden" value="false" name="V_REGIONsearchIndel" />
		<input type="hidden" value="-1" name="nbD_GENE" />
		<input type="hidden" value="-1" name="nbVmut" />
		<input type="hidden" value="-1" name="nbDmut" />
		<input type="hidden" value="-1" name="nbJmut" />
		<input type="hidden" value="0" name="nb5V_REGIONignoredNt" />
		<input type="hidden" value="0" name="nb3V_REGIONaddedNt" />
		<input type="hidden" value="false" name="scfv" />
		<input type="hidden" value="false" name="cllSubsetSearch" />
		
	  </form>
	  
	</div>
  );
}

