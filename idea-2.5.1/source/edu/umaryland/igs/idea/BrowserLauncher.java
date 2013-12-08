package edu.umaryland.igs.idea;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

// The JDesktop library is used to launch the user's default web browser in Linux and Solaris.
import org.jdesktop.jdic.desktop.Desktop;
import org.jdesktop.jdic.desktop.DesktopException;

/**
 * <code>BrowserLauncher</code> contains code that launches the user's default web browser.
 * In Linux and Solaris, this is accomplished using JDesktop.
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
public class BrowserLauncher{
	
	/**
	 * The <code>launchBrowser</code> method launches loads the specified URL in the user's default web browser.
	 * In the event of any error, the user is instead referred to the URL.
	 *
	 * @param url the page to load
	 * @param owner the frame to which error dialogs should be attached
	 */
	public static void launchBrowser(String url, final JFrame owner){
		String browserFailure = "Your default web browser could not be started.\nYou may not have a default web browser.\nOnline help is available at " + url + " .";
		String osName = System.getProperty("os.name");
		if ((osName != null) && osName.startsWith("Mac OS")){
	    try{
				Class classFileManager = Class.forName("com.apple.eio.FileManager");
				Method openURL = classFileManager.getDeclaredMethod("openURL", new Class[] {String.class});
				openURL.invoke(null, url);
	    }
	    catch (Exception ex){
				JOptionPane.showMessageDialog(owner, browserFailure, "Error Starting Browser", JOptionPane.ERROR_MESSAGE);
	    }
	    catch (LinkageError le){
				JOptionPane.showMessageDialog(owner, browserFailure, "Error Starting Browser", JOptionPane.ERROR_MESSAGE);
	    }
		}
		else{
	    try{
				Desktop.browse(new URL(url));
	    }
	    catch (MalformedURLException murle){
				JOptionPane.showMessageDialog(owner,
																			url + " could not be loaded.\nThis may be a connection problem.",
																			"Page Could Not Be Loaded",
																			JOptionPane.ERROR_MESSAGE);
	    }
	    catch (DesktopException de){
				JOptionPane.showMessageDialog(owner, browserFailure, "Error Starting Browser", JOptionPane.ERROR_MESSAGE);
				de.printStackTrace();
	    }
	    catch (LinkageError le){
				JOptionPane.showMessageDialog(owner, browserFailure, "Error Starting Browser", JOptionPane.ERROR_MESSAGE);
				le.printStackTrace();
	    }
		}
	}

}
