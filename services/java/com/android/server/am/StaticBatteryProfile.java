package com.android.server.am;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import android.os.BatteryManager;
import android.os.BatteryStats.HistoryItem;

public class StaticBatteryProfile {

	/**
	 * @param args
	 */
	public static List<HistoryItem> getProfileEntries(){
			List<HistoryItem> l = new ArrayList<HistoryItem>();
			final String path = "/data/system/battery_log.csv";
			CSVReader csv;
			try {
				String s[];
				long timestamp, offset = 0, now = 0;
				int percent, charging;
				float voltage;
				
				// First find last timestamp
				csv = new CSVReader(new FileReader(path));
				s = csv.readNext();
				while(s != null){
					offset = Long.valueOf(s[0]);
					s = csv.readNext();
				}
				csv.close();

				csv = new CSVReader(new FileReader(path));
				s = csv.readNext();
				while(s != null){
					if(now == 0){
						now = System.currentTimeMillis() / 1000L;
					}
					
					// Get values from entry
					timestamp = Long.valueOf(s[0]) - offset + now;
					percent = Integer.valueOf(s[1]);
					charging = Integer.valueOf(s[4]);
					//voltage = Float.valueOf(s[2]);
					
					HistoryItem h = new HistoryItem();
					h.time = timestamp;
					h.batteryLevel = (byte) percent;
					if(charging == 1)
						h.batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING;
					else
						h.batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING;
					//h.batteryVoltage;
					l.add(h);
					
					//System.out.println(timestamp);
					s = csv.readNext();
				}
				csv.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return l;
	}
	/*
	 * The code copied from http://opencsv.sourceforge.net/
	 *
	 * While incorporating into secrets, the following changes were made:
	 *
	 * - Added support of generics
	 * - removed the following methods to keep the bytecode smaller:
	 *   readAll(), some constructors
	 */

	/**
	 * A very simple CSV reader released under a commercial-friendly license.
	 *
	 * @author Glen Smith
	 *
	 */
	private static class CSVReader {

	    private BufferedReader br;

	    private boolean hasNext = true;

	    private char separator;

	    private char quotechar;

	    private int skipLines;

	    private boolean linesSkiped;

	    /** The default separator to use if none is supplied to the constructor. */
	    public static final char DEFAULT_SEPARATOR = ',';

	    /**
	     * The default quote character to use if none is supplied to the
	     * constructor.
	     */
	    public static final char DEFAULT_QUOTE_CHARACTER = '"';

	    /**
	     * The default line to start reading.
	     */
	    public static final int DEFAULT_SKIP_LINES = 0;

	    /**
	     * Constructs CSVReader using a comma for the separator.
	     *
	     * @param reader
	     *            the reader to an underlying CSV source.
	     */
	    public CSVReader(Reader reader) {
	        this(reader, DEFAULT_SEPARATOR, DEFAULT_QUOTE_CHARACTER,
	            DEFAULT_SKIP_LINES);
	    }

	    /**
	     * Constructs CSVReader with supplied separator and quote char.
	     *
	     * @param reader
	     *            the reader to an underlying CSV source.
	     * @param separator
	     *            the delimiter to use for separating entries
	     * @param quotechar
	     *            the character to use for quoted elements
	     * @param line
	     *            the line number to skip for start reading
	     */
	    public CSVReader(Reader reader, char separator, char quotechar, int line) {
	        this.br = new BufferedReader(reader);
	        this.separator = separator;
	        this.quotechar = quotechar;
	        this.skipLines = line;
	    }

	    /**
	     * Reads the next line from the buffer and converts to a string array.
	     *
	     * @return a string array with each comma-separated element as a separate
	     *         entry.
	     *
	     * @throws IOException
	     *             if bad things happen during the read
	     */
	    public String[] readNext() throws IOException {

	        String nextLine = getNextLine();
	        return hasNext ? parseLine(nextLine) : null;
	    }

	    /**
	     * Reads the next line from the file.
	     *
	     * @return the next line from the file without trailing newline
	     * @throws IOException
	     *             if bad things happen during the read
	     */
	    private String getNextLine() throws IOException {
	        if (!this.linesSkiped) {
	            for (int i = 0; i < skipLines; i++) {
	                br.readLine();
	            }
	            this.linesSkiped = true;
	        }
	        String nextLine = br.readLine();
	        if (nextLine == null) {
	            hasNext = false;
	        }
	        return hasNext ? nextLine : null;
	    }

	    /**
	     * Parses an incoming String and returns an array of elements.
	     *
	     * @param nextLine
	     *            the string to parse
	     * @return the comma-tokenized list of elements, or null if nextLine is null
	     * @throws IOException if bad things happen during the read
	     */
	    private String[] parseLine(String nextLine) throws IOException {

	        if (nextLine == null) {
	            return null;
	        }

	        List<String> tokensOnThisLine = new ArrayList<String>();
	        StringBuffer sb = new StringBuffer();
	        boolean inQuotes = false;
	        do {
	                if (inQuotes) {
	                // continuing a quoted section, reappend newline
	                sb.append("\n");
	                nextLine = getNextLine();
	                if (nextLine == null)
	                    break;
	            }
	            for (int i = 0; i < nextLine.length(); i++) {

	                char c = nextLine.charAt(i);
	                if (c == quotechar) {
	                        // this gets complex... the quote may end a quoted block, or escape another quote.
	                        // do a 1-char lookahead:
	                        if( inQuotes  // we are in quotes, therefore there can be escaped quotes in here.
	                            && nextLine.length() > (i+1)  // there is indeed another character to check.
	                            && nextLine.charAt(i+1) == quotechar ){ // ..and that char. is a quote also.
	                                // we have two quote chars in a row == one quote char, so consume them both and
	                                // put one on the token. we do *not* exit the quoted text.
	                                sb.append(nextLine.charAt(i+1));
	                                i++;
	                        }else{
	                                inQuotes = !inQuotes;
	                                // the tricky case of an embedded quote in the middle: a,bc"d"ef,g
	                                if(i>2 //not on the begining of the line
	                                                && nextLine.charAt(i-1) != this.separator //not at the begining of an escape sequence
	                                                && nextLine.length()>(i+1) &&
	                                                nextLine.charAt(i+1) != this.separator //not at the     end of an escape sequence
	                                ){
	                                        sb.append(c);
	                                }
	                        }
	                } else if (c == separator && !inQuotes) {
	                    tokensOnThisLine.add(sb.toString());
	                    sb = new StringBuffer(); // start work on next token
	                } else {
	                    sb.append(c);
	                }
	            }
	        } while (inQuotes);
	        tokensOnThisLine.add(sb.toString());
	        return (String[]) tokensOnThisLine.toArray(new String[0]);

	    }

	    /**
	     * Closes the underlying reader.
	     *
	     * @throws IOException if the close fails
	     */
	    public void close() throws IOException{
	        br.close();
	    }

	}
}
