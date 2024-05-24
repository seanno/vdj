//
// TSVREADER.JAVA
// 

package com.shutdownhook.vdj.vdjlib;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.shutdownhook.toolbox.Easy;

import com.shutdownhook.vdj.vdjlib.model.FrameType;
import com.shutdownhook.vdj.vdjlib.model.Locus;
import com.shutdownhook.vdj.vdjlib.model.Rearrangement;

public class TsvReader implements Closeable
{
	public interface LinePeeker {
		public void peek(String line) throws IOException;
	}

	// +------------------+
	// | Setup & Teardown |
	// +------------------+
	
	public TsvReader(InputStreamReader stm, int startRowIndex) {
		this(stm, startRowIndex, null);
	}
	
	public TsvReader(InputStreamReader stm, int startRowIndex, LinePeeker peeker) {
		this.stm = stm;
		this.startRowIndex = startRowIndex;
		this.peeker = peeker;
		this.cellCount = null; 
	}
	
	public void close() {

		// we don't own stm so leave it alone

		if (rdr != null) Easy.safeClose(rdr);
	}

	// +-----------------+
	// | getNextRowIndex |
	// +-----------------+

	public int getNextRowIndex() {
		return(nextRowIndex);
	}

	public Long getDiscoveredCellCount() {
		return(cellCount);
	}

	// +--------------------+
	// | readNext           |
	// | readNextBatch      |
	// | readNextBatchAsync |
	// +--------------------+

	public CompletableFuture<List<Rearrangement>> readNextBatchAsync(int maxCount) {

		CompletableFuture<List<Rearrangement>> future =
			new CompletableFuture<List<Rearrangement>>();

		Exec.getPool().submit(() -> {
			List<Rearrangement> results = null;
			try {
				results = readBatch(maxCount);
			}
			catch (Exception e) {
				log.warning(Easy.exMsg(e, "readNextBatchAsync", true));
			}
			
			future.complete(results);
		});

		return(future);
	}

	public List<Rearrangement> readBatch(int maxCount) throws IOException {

		List<Rearrangement> batch = new ArrayList<Rearrangement>();

		Rearrangement r;
		
		while (batch.size() < maxCount && (r = readNext()) != null) {
			batch.add(r);
		}

		return(batch.size() == 0 ? null : batch);
	}

	public Rearrangement readNext() throws IOException {

		if (rdr == null) initialize();
		
		String line = readLine();
		if (line == null) return(null);
		
		++nextRowIndex;
		String[] fields = line.split(TSV_SEP);

		Rearrangement r = new Rearrangement();

		// string values
		
		r.Rearrangement = fields[headerIndices[IHDR_REARRANGEMENT]].trim();
		r.AminoAcid = fields[headerIndices[IHDR_AMINOACID]].trim();
		r.VResolved = fields[headerIndices[IHDR_VRESOLVED]].trim();
		r.DResolved = fields[headerIndices[IHDR_DRESOLVED]].trim();
		r.JResolved = fields[headerIndices[IHDR_JRESOLVED]].trim();
		
		// parsed values

		String strFrameType = fields[headerIndices[IHDR_FRAMETYPE]].trim();
		String strCount = fields[headerIndices[IHDR_COUNT]].trim();
		String strCdr3Length = fields[headerIndices[IHDR_CDR3LENGTH]].trim();
		String strVIndex = fields[headerIndices[IHDR_VINDEX]].trim();
		String strDIndex = fields[headerIndices[IHDR_DINDEX]].trim();
		String strJIndex = fields[headerIndices[IHDR_JINDEX]].trim();
		String strN1Index = fields[headerIndices[IHDR_N1INDEX]].trim();
		String strN2Index = fields[headerIndices[IHDR_N2INDEX]].trim();
		String strVSHMIndices = optionalField(fields, IHDR_VSHMINDICES);

		r.FrameType = FrameType.valueOf(strFrameType);
		r.Count = Long.parseLong(strCount);
		r.Cdr3Length = Integer.parseInt(strCdr3Length);
		r.VIndex = Integer.parseInt(strVIndex);
		r.DIndex = Integer.parseInt(strDIndex);
		r.JIndex = Integer.parseInt(strJIndex);
		r.N1Index = Integer.parseInt(strN1Index);
		r.N2Index = Integer.parseInt(strN2Index);

		r.Locus = Locus.fromGene(r.VResolved, r.DResolved, r.JResolved);

		r.VSHMIndices = null;
		if (!Easy.nullOrEmpty(strVSHMIndices)) {
			String[] csvVSHMIndices = strVSHMIndices.split(",");
			r.VSHMIndices = new int[csvVSHMIndices.length];
			for (int i = 0; i < csvVSHMIndices.length; ++i) {
				r.VSHMIndices[i] = Integer.parseInt(csvVSHMIndices[i]);
			}
		}

		if (cellCount == null) {

			if (headerIndices[IHDR_CELLS] != IDX_MISSING_FIELD) {
				String strCells = fields[headerIndices[IHDR_CELLS]].trim();
				if (!strCells.isEmpty()) cellCount = Long.parseLong(strCells);
			}

			if (cellCount == null && headerIndices[IHDR_CELLS_EST] != IDX_MISSING_FIELD) {
				String strCells = fields[headerIndices[IHDR_CELLS_EST]].trim();
				if (!strCells.isEmpty()) cellCount = Long.parseLong(strCells);
			}
		}

		return(r);
	}

	private String optionalField(String[] fields, int ihdr) {
		int idx = headerIndices[ihdr];
		if (idx == IDX_MISSING_FIELD || idx >= fields.length) return(null);
		return(fields[idx]);
	}

	// +------------+
	// | initialize |
	// +------------+

	private void initialize() throws IOException {
		
		this.rdr = new BufferedReader(stm);

		setupHeaders();
		advanceToRow(startRowIndex);
	}
	
	private void setupHeaders() throws IOException {

		// this assumes there is at least one header line in the file, 
		// which is true Adaptive TSVs. It also assumes that all fields
		// are present except possibly VSHMIndices.

		String line;
		
		while ((line = readLine()) != null) {
			
			if (line == null) break;
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

			headerIndices = new int[HEADER_COUNT];
			for (int i = 0; i < headerIndices.length; ++i) {
				headerIndices[i] = IDX_MISSING_FIELD;
			}

			String[] headers = trimmed.split(TSV_SEP);
			
			for (int i = 0; i < headers.length; ++i) {
				setHeaderPosition(i, headers[i].trim().toLowerCase());
			}

			break;
		}
	}

	private void setHeaderPosition(int i, String header) {

		int index = -1;
		
		switch (header) {

			// V2
			case "nucleotide":  index = IHDR_REARRANGEMENT; break;
			case "aminoacid": index = IHDR_AMINOACID; break;
			case "sequencestatus": index = IHDR_FRAMETYPE; break;
			case "count (templates/reads)": index = IHDR_COUNT; break;
			case "vmaxresolved": index = IHDR_VRESOLVED; break;
			case "dmaxresolved": index = IHDR_DRESOLVED; break;
			case "jmaxresolved": index = IHDR_JRESOLVED; break;
			case "cdr3length": index = IHDR_CDR3LENGTH; break;
			case "vindex": index = IHDR_VINDEX; break;
			case "dindex": index = IHDR_DINDEX; break;
			case "jindex": index = IHDR_JINDEX; break;
			case "n1index": index = IHDR_N1INDEX; break;
			case "n2index": index = IHDR_N2INDEX; break;
			case "valignsubstitutionindexes": index = IHDR_VSHMINDICES; break;

			// V3
			case "rearrangement":  index = IHDR_REARRANGEMENT; break;
			case "amino_acid": index = IHDR_AMINOACID; break;
			case "frame_type": index = IHDR_FRAMETYPE; break;
			case "templates": index = IHDR_COUNT; break;
			case "v_resolved": index = IHDR_VRESOLVED; break;
			case "d_resolved": index = IHDR_DRESOLVED; break;
			case "j_resolved": index = IHDR_JRESOLVED; break;
			case "cdr3_length": index = IHDR_CDR3LENGTH; break;
			case "v_index": index = IHDR_VINDEX; break;
			case "d_index": index = IHDR_DINDEX; break;
			case "j_index": index = IHDR_JINDEX; break;
			case "n1_index": index = IHDR_N1INDEX; break;
			case "n2_index": index = IHDR_N2INDEX; break;
			case "v_shm_indexes": index = IHDR_VSHMINDICES; break;
			case "sample_cells": index = IHDR_CELLS; break;
			case "sample_cells_mass_estimate": index = IHDR_CELLS_EST; break;
		}

		if (index != -1) headerIndices[index] = i;
	}
	
	private void advanceToRow(int startRowIndex) throws IOException {

		nextRowIndex = 0;

		while (nextRowIndex < startRowIndex && (readLine() != null)) {
			++nextRowIndex;
		}
	}

	// +---------+
	// | Helpers |
	// +---------+

	private String readLine() throws IOException {
		String line = rdr.readLine();
		if (line != null && peeker != null) peeker.peek(line);
		return(line);
	}
		
	// +---------+
	// | Members |
	// +---------+

	protected InputStreamReader stm; // protected for test code
	private int startRowIndex;
	private LinePeeker peeker;
	
	private BufferedReader rdr;
	private int nextRowIndex;
	private int[] headerIndices;

	private Long cellCount;

	private final static Logger log = Logger.getLogger(TsvReader.class.getName());

	// +----------------------+
	// | TSV Header Constants |
	// +----------------------+

	private static String TSV_SEP = "\t";

	public static int IDX_MISSING_FIELD = -1;
			
	private static int IHDR_REARRANGEMENT = 0;
	private static int IHDR_AMINOACID = 1;
	private static int IHDR_FRAMETYPE = 2;
	private static int IHDR_COUNT = 3;
	private static int IHDR_VRESOLVED = 4;
	private static int IHDR_DRESOLVED = 5;
	private static int IHDR_JRESOLVED = 6;
	private static int IHDR_CDR3LENGTH = 7;
	private static int IHDR_VINDEX = 8;
	private static int IHDR_DINDEX = 9;
	private static int IHDR_JINDEX = 10;
	private static int IHDR_N1INDEX = 11;
	private static int IHDR_N2INDEX = 12;
	private static int IHDR_VSHMINDICES = 13;

	private static int IHDR_CELLS = 14;
	private static int IHDR_CELLS_EST = 15;
	
	private static int HEADER_COUNT = 16; // KEEP ME UPDATED!
}
