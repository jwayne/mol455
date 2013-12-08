package edu.umaryland.igs.idea;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.FontConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.font.TransformAttribute;
import java.awt.geom.AffineTransform;
import java.util.Map;

import javax.swing.plaf.FontUIResource;

/**
 * <code>FaultTolerantFontConverter</code> is designed to help XStream read
 * fonts from a saved configuration.  It supplies a transform text
 * attribute for serialized fonts missing it.
 * 
 * <p>Written:
 *
 * <p>Copyright (C) 2007, Amy Egan and Joana C. Silva.
 *
 * <p>All rights reserved.
 *
 *
 * @author Amy Egan
 */

public class FaultTolerantFontConverter extends FontConverter{

	/**
	 * The <code>unmarshal</code> method creates a font based from an XML representation of that font.
	 * If the XML is missing a transform attribute, it supplies an identity transform.
	 *
	 * @param reader the stream from which to read the text
	 * @param context an unmarshaller which can handle conversions of child attributes or delegate them appropriately
	 *
	 * @return a <code>Font</code> or <code>FontUIResource</code>, depending on what is read in
	 */
	@SuppressWarnings("unchecked")
	public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context){
		reader.moveDown();
		Map attributes = (Map) context.convertAnother(null, Map.class);
		if (attributes.get(TextAttribute.TRANSFORM) == null){
			attributes.put(TextAttribute.TRANSFORM, new TransformAttribute(new AffineTransform()));
		}
		reader.moveUp();
		Font font = Font.getFont(attributes);
		if (context.getRequiredType() == FontUIResource.class) {
			return new FontUIResource(font);
		}
		else {
			return font;
		}
	}
}
