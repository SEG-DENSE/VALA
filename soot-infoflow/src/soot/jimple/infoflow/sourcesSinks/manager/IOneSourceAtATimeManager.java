package soot.jimple.infoflow.sourcesSinks.manager;

import soot.SootField;

/**
 * Interface for source/sink managers that support calculating data flows with
 * one source at a time
 * 
 * @author Steven Arzt
 *
 */
public interface IOneSourceAtATimeManager {
	
	/**
	 * Sets whether this source/sink manager shall run with one source at a time
	 * instead of all of them together
	 * @param enabled True to return only one source at a time and hide all other
	 * ones, otherwise false
	 */
    void setOneSourceAtATimeEnabled(boolean enabled);
	
	/**
	 * Gets whether this source/sink manager shall run with one source at a time
	 * instead of all of them together
	 * @return True to return only one source at a time and hide all other ones,
	 * otherwise false
	 */
    boolean isOneSourceAtATimeEnabled();
	
	/**
	 * Resets the iterator. This means that the source/sink manager starts again
	 * with the first source.
	 */
//    void resetCurrentSource();
	
	/**
	 * Advances the iterator to the next source.
	 */
    void nextSource();
	
	/**
	 * Checks whether there is another source with which to run the data flow
	 * analysis
	 * @return True if there is another source with which to run the data flow
	 * analysis, otherwise false
	 */
    boolean hasNextSource();

	SootField getCurrentSourceField();

}
