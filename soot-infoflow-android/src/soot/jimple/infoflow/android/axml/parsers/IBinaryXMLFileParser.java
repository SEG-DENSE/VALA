package soot.jimple.infoflow.android.axml.parsers;

import soot.jimple.infoflow.android.axml.AXmlDocument;
import soot.jimple.infoflow.android.axml.AXmlNode;

import java.io.IOException;
import java.util.List;

/**
 * Common interface for all methods capable of parsing binary Android XML files
 * 
 * @author Steven Arzt
 */
public interface IBinaryXMLFileParser {

	/**
	 * Parses the binary XML file with the AXMLPrinter2 library
	 * @param	buffer					The buffer to be parsed
	 * @throws IOException                if an I/O error occurs.
	 */
    void parseFile(byte[] buffer) throws IOException;

	/**
	 * Returns the Android XML document.
	 * 
	 * @return	the Android XML document
	 */
    AXmlDocument getDocument();

	/**
	 * Returns a list containing all nodes of the xml document which have the given tag.
	 * 
	 * @param	tag		the tag being search for
	 * @return	list pointing on all nodes which have the given tag.
	 */
    List<AXmlNode> getNodesWithTag(String tag);

}
