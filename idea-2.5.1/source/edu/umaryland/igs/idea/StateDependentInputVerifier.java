package edu.umaryland.igs.idea;

/**
 * <code>StateDependentInputVerifier</code> is an extension of <code>TextFieldInputVerifier</code>
 * common to text fields whose validation depends on the current values of other parameters or
 * perhaps on other aspects of IDEA's current state.
 *
 * <p>Written:
 *
 * <p>Copyright (C) 2007, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 *
 */

public abstract class StateDependentInputVerifier extends TextFieldInputVerifier implements Cloneable{

	protected IDEAConfiguration currentState;  // the state on which to base validation

	/**
	 * The constructor for <code>StateDependentInputVerifier</code> takes an <code>IDEAConfiguration</code>
	 * which is passed by reference, so modifications to it after the input verifier is created have an
	 * effect on subsequent validations.
	 *
	 * @param config the state on which to base validation
	 */
	public StateDependentInputVerifier(IDEAConfiguration config){
		currentState = config;
	}

	/**
	 * @deprecated
	 */
	public Object clone(){
		try{
			StateDependentInputVerifier rv = (StateDependentInputVerifier) super.clone();
			// Don't copy currentState.  Rely on IDEAConfiguration to handle it.
			return rv;
		}
		catch (CloneNotSupportedException cnse){
			return null;
		}
	}

	/**
	 * @deprecated
	 */
	void setCloneConfig(IDEAConfiguration alreadyCopiedConfig){
		currentState = alreadyCopiedConfig;
	}

}
