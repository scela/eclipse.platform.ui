org.eclipse.ui.images.renderer
==============================

org.eclipse.ui.images provides the a Maven generator of svg images located in the org.eclipse.ui.images plug-in. 

org.eclipse.ui.images.renderer plug-in usage
--------------------------------------------

Install the org.eclipse.ui.images.renderer plug-in:

cd org.eclipse.ui.images.renderer
mvn clean install

After the renderer plugin is installed, change into the root of the images project:

cd org.eclipse.ui.images

Finally, execute the icon render mojo with:

mvn org.eclipse.ui:org.eclipse.ui.images.renderer:render-icons

This renders all of the svg icons in "eclipse-svg" into the "eclipse-png" folder of the org.eclipse.ui.images project, maintaining the directory structure (i.e. eclipse-svg/icondir will be rendered into org.eclipse.ui.images/eclipse-png/icondir).


License
-------

[Eclipse Public License (EPL) v1.0][2]

[1]: http://wiki.eclipse.org/Platform_UI
[2]: http://wiki.eclipse.org/EPL