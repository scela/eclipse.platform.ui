/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.resource;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

/**
 * A weakly referenced cache of image descriptors to arrays of image instances
 * (representing normal, gray and disabled images). This is used to hold images
 * in memory while their descriptors are defined. When the image descriptors
 * become weakly referred to, the corresponding images in the array of images
 * will be disposed.
 * 
 * Weak references of equivalent image descriptors are mapped to the same array
 * of images (where equivalent descriptors are <code>equals(Object)</code>).
 * 
 * This cache makes no guarantees on how long the cleaning process will take, or
 * when exactly it will occur.
 * 
 * Upon request of a disabled or gray image, the normal image will be created as
 * well (if it was not already in the cache) in order to create the disabled or
 * gray version of the image.
 * 
 * It is the responsibility of the user to ensure that the image descriptors are
 * kept around as long as the images are needed. The users of this cache should
 * not explicitly dispose the images.
 * 
 * It is recommended to use this class as a singleton, since it creates a thread
 * for cleaning out images.
 * 
 * This class may be instantiated; it is not intended to be subclassed.
 * 
 * NOTE: This API is experimental and is subject to change, including removal.
 * 
 * @since 3.1
 */
public final class ImageCache {

    /**
     * An equivalent set of weak references to equivalent descriptors. The
     * equivalence of image descriptors is determined through
     * <code>equals(Object)</code>.
     * 
     * @since 3.1
     */
    private static final class EquivalenceSet {

        /**
         * The equivalence set's hash code is the hash code of the first weak
         * reference added to the set.
         */
        private final int equivalenceHashCode;

        /**
         * A list of weak references to equivalent image descriptors.
         */
        private final ArrayList imageCacheWeakReferences;

        /**
         * Create an equivalence set and add the weak reference to the list.
         * 
         * @param referenceToAdd
         *            The weak reference to add to the list of weak references.
         */
        private EquivalenceSet(ImageCacheWeakReference referenceToAdd) {
            imageCacheWeakReferences = new ArrayList();
            imageCacheWeakReferences.add(referenceToAdd);
            // The equivalence hash code will be the hash code of the first
            // inserted weak reference
            equivalenceHashCode = referenceToAdd.getCachedHashCode();
        }

        /**
         * Add a weak refrence to the equivalence set. This method assumes that
         * the reference to add does belong in this set.
         * 
         * @param referenceToAdd
         *            The weak reference to add.
         * @return true if the weak reference was added to the set, and false if
         *         the reference already exists in the set.
         */
        public boolean addWeakReference(ImageCacheWeakReference referenceToAdd) {
            // Only add the weak reference if it does not already exist
            ImageCacheWeakReference weakReference = null;
            for (Iterator i = imageCacheWeakReferences.iterator(); i.hasNext();) {
                weakReference = (ImageCacheWeakReference) i.next();
                // "referenceToAdd.get()" will not be null, but
                // "weakReference.get()" could be null, which is ok since we
                // should add the element since its "identity" will be removed
                // shortly.
                if (referenceToAdd.get() == weakReference.get()) {
                    return false;
                }
            }
            imageCacheWeakReferences.add(referenceToAdd);
            return true;
        }

        /**
         * Clear the weak references in this equivalence set.
         *  
         */
        public void clear() {
            ImageCacheWeakReference currentReference = null;
            for (Iterator i = imageCacheWeakReferences.iterator(); i.hasNext();) {
                currentReference = (ImageCacheWeakReference) i.next();
                currentReference.clear();
            }
            imageCacheWeakReferences.clear();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        public boolean equals(Object object) {
            // Two sets are equivalent if their descriptors
            // are "equal"
            ImageDescriptor reachableDescriptor = null;
            if (!(object instanceof EquivalenceSet)) {
                return false;
            }

            // Retrieve an image descriptor in the set of weak references
            // that has not been enqueued
            reachableDescriptor = ((EquivalenceSet) object)
                    .getFirstReachableDescriptor();
            if (reachableDescriptor == null) {
                return false;
            }
            // Manipulating descriptors themselves just in case the referent
            // gets cleaned by the time we reach this part.
            return reachableDescriptor.equals(getFirstReachableDescriptor());

        }

        /**
         * Get a non-null image descriptor from the list of weak references to
         * image descriptors.
         * 
         * @return a non null image descriptor, or null if none could be found.
         */
        public ImageDescriptor getFirstReachableDescriptor() {
            ImageDescriptor referent = null;
            for (Iterator i = imageCacheWeakReferences.iterator(); i.hasNext();) {
                referent = (ImageDescriptor) ((ImageCacheWeakReference) i
                        .next()).get();
                if (referent != null) {
                    // return descriptor itself. This way, we have a reference
                    // to it and it won't be cleared by the time we return from
                    // this method
                    return referent;
                }
            }
            return null;
        }

        /**
         * Return the number of items in the list of weak references.
         * 
         * @return the number of items in the list of weak references.
         */
        public int getSize() {
            return imageCacheWeakReferences.size();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        public int hashCode() {
            return equivalenceHashCode;
        }

        /**
         * Remove a hashable weak reference from the list. This method makes no
         * assumptions as to whether the reference to remove belongs in this
         * equivalence set or not.
         * 
         * @param referenceToRemove
         *            The weak reference to remove.
         * @return true if the reference was removed succesfully.
         */
        public boolean removeReference(ImageCacheWeakReference referenceToRemove) {
            return imageCacheWeakReferences.remove(referenceToRemove);
        }

    }

    /**
     * A wrapper around the weak reference to imae descriptors in order to be
     * able to store the referrent's hash code since it will be null when
     * enqueued.
     * 
     * @since 3.1
     */
    private static final class ImageCacheWeakReference extends WeakReference {
        /**
         * Referent's hash code since it will not be available once the
         * reference has been enqueued.
         */
        private final int referentHashCode;

        /**
         * Creates a weak reference for an image descriptor.
         * 
         * @param referent
         *            The image descriptor. Will not be <code>null</code>.
         * @param queue
         *            The reference queue.
         */
        public ImageCacheWeakReference(Object referent, ReferenceQueue queue) {
            super(referent, queue);
            referentHashCode = referent.hashCode();
        }

        /**
         * The referent's cached hash code value.
         * 
         * @return the referent's cached hash code value.
         */
        public int getCachedHashCode() {
            return referentHashCode;
        }

    }

    /**
     * An entry in the image map, which consists of the array of images (the
     * value), as well as the key. This allows to retrieve BOTH the key (the
     * equivalence set) and the value (the array of images) from the map
     * directly.
     * 
     * @since 3.1
     */
    private static final class ImageMapEntry {
        /**
         * The array of images.
         */
        private final Image[] entryImages;

        /**
         * The equivalence set.
         */
        private final EquivalenceSet entrySet;

        /**
         * Create an entry that consists of the equivalence set (key) as well as
         * the array of images.
         * 
         * @param equivalenceSet
         *            The equivalence set.
         * @param images
         *            The array of images.
         */
        public ImageMapEntry(EquivalenceSet equivalenceSet, Image[] images) {
            this.entrySet = equivalenceSet;
            this.entryImages = images;
        }

        /**
         * Return the equivalence set in this entry. Should not be
         * <code>null</code>.
         * 
         * @return the entry set.
         */
        public EquivalenceSet getEquivalenceSet() {
            return entrySet;
        }

        /**
         * Return the array of images in this entry. Should not be
         * <code>null</code>.
         * 
         * @return the array of images.
         */
        public Image[] getImages() {
            return entryImages;
        }

    }

    /**
     * A thread for cleaning up the reference queues as the garbage collector
     * fills them. It takes an image map and a reference queue. When an item
     * appears in the reference queue, it uses it as a key to remove values from
     * the map. If the value is an array of images, then the defined images in
     * that array are is disposed. To shutdown the thread, call
     * <code>stopCleaning()</code>.
     * 
     * @since 3.1
     */
    private static class ReferenceCleanerThread extends Thread {

        /**
         * The number of reference cleaner threads created.
         */
        private static int threads = 0;

        /**
         * A marker indicating that the reference cleaner thread should exit.
         * This is enqueued when the thread is told to stop. Any referenced
         * enqueued after the thread is told to stop will not be cleaned up.
         */
        private final ImageCacheWeakReference endMarker;

        /**
         * A map of equivalence sets to ImageMapEntry (Image[3],
         * EquivalenceSet).
         */
        private final Map imageMap;

        /**
         * The reference queue to check.
         */
        private final ReferenceQueue referenceQueue;

        /**
         * Constructs a new instance of <code>ReferenceCleanerThread</code>.
         * 
         * @param referenceQueue
         *            The reference queue to check for garbage.
         * @param map
         *            Map of equivalence sets to ImageMapEntry (Image[3],
         *            EquivalenceSet).
         */
        private ReferenceCleanerThread(final ReferenceQueue referenceQueue,
                final Map map) {
            super("Reference Cleaner: " + ++threads); //$NON-NLS-1$

            if (referenceQueue == null) {
                throw new NullPointerException(
                        "The reference queue should not be null.");//$NON-NLS-1$
            }

            if (map == null) {
                throw new NullPointerException("The map should not be null.");//$NON-NLS-1$
            }

            this.endMarker = new ImageCacheWeakReference(referenceQueue,
                    referenceQueue);
            this.referenceQueue = referenceQueue;
            this.imageMap = map;
        }

        /**
         * Waits for new garbage. When new garbage arriving, it removes it,
         * clears it, and disposes of any corresponding images.
         */
        public final void run() {
            while (true) {
                // Get the next reference to dispose.
                Reference reference = null;
                try {
                    reference = referenceQueue.remove();
                } catch (final InterruptedException e) {
                    // Reference will be null.
                }

                // Check to see if we've been told to stop.
                if (reference == endMarker) {
                    break;
                }

                // Image disposal - need to traverse the set of keys, since the
                // image descriptor has been cleaned. No way to directly
                // retrieve the equivalence set from the map . This could be
                // improved (with better search/sort).
                if (reference instanceof ImageCacheWeakReference) {
                    ImageCacheWeakReference currentReference = (ImageCacheWeakReference) reference;
                    Set keySet = imageMap.keySet();
                    EquivalenceSet currentSet = null;
                    // Traverse the set of keys to find corresponding
                    // equivalence set
                    for (Iterator i = keySet.iterator(); i.hasNext();) {
                        currentSet = (EquivalenceSet) i.next();
                        boolean removed = currentSet
                                .removeReference(currentReference);
                        if (removed) {
                            // Clean up needed since the set is now empty
                            if (currentSet.getSize() == 0) {
                                final Image[] images = ((ImageMapEntry) imageMap
                                        .remove(currentSet)).getImages();
                                if (images == null) {
                                    throw new NullPointerException(
                                            "The array of images removed from the map on clean up should not be null."); //$NON-NLS-1$
                                }

                                Display display = Display.getCurrent();
                                if (display == null) {
                                    display = Display.getDefault();
                                }
                                display.syncExec(new Runnable() {
                                    public void run() {
                                        // Dispose the images
                                        for (int i = 0; i < images.length; i++) {
                                            final Image image = images[i];
                                            if ((image != null)
                                                    && (!image.isDisposed())) {
                                                image.dispose();
                                            }
                                        }
                                    }
                                });
                            }
                            // break out of for loop since the refernece has ben
                            // removed
                            break;
                        }

                    }

                }

                // Clear the reference.
                if (reference != null) {
                    reference.clear();
                }

            }
        }

        /**
         * Tells this thread to stop trying to clean up. This is usually run
         * when the cache is shutting down.
         */
        private final void stopCleaning() {
            endMarker.enqueue();
        }
    }

    /**
     * Types of images supported by the image cache.
     */
    private static final int DISABLED = 0;

    private static final int GRAY = 1;

    private static final int NORMAL = 2;

    private static final int TYPES_OF_IMAGES = 3;

    /**
     * The thread responsible for cleaning out images that are no longer needed.
     * The images in Image[3] will be cleaned if the corresponding equivalence
     * set contains no more weak references to image descriptor.
     */
    private final ReferenceCleanerThread imageCleaner;

    /**
     * A map of equivalence sets to ImageMapEntry (Image[3], EquivalenceSet).
     * The equivalence set represents a list of weakly referenced image
     * descriptors that are equivalent ("equal"). The equivalence set will
     * contain no duplicate image descriptor references (check for identical
     * descriptors on addition using "==").
     */
    private final Map imageMap;

    /**
     * A queue of references (<code>HashableWeakReference</code>) waiting to
     * be garbage collected. This value is never <code>null</code>. This is
     * the queue for <code>imageMap</code>.
     */
    private final ReferenceQueue imageReferenceQueue;

    /**
     * The image to display when no image is available. This value is
     * <code>null</code> until it is first used, and will not get disposed
     * until the image cache itself is disposed.
     */
    private Image missingImage = null;

    /**
     * Constructs a new instance of <code>ImageCache</code>, and starts a
     * thread to monitor the reference queue.
     */
    public ImageCache() {
        imageMap = new HashMap();

        imageReferenceQueue = new ReferenceQueue();
        imageCleaner = new ReferenceCleanerThread(imageReferenceQueue, imageMap);
        imageCleaner.start();
    }

    /**
     * Constructs a new instance of <code>ImageCache</code>, and starts a
     * thread to monitor the reference queue. If the passed initial load
     * capacity is negative, the image map is created with the default
     * <code>HashMap</code> constructor.
     * 
     * @param initialLoadCapacity
     *            Initial load capacity for the image hash map.
     */
    public ImageCache(final int initialLoadCapacity) {
        if (initialLoadCapacity < 0) {
            imageMap = new HashMap();
        } else {
            imageMap = new HashMap(initialLoadCapacity);
        }

        imageReferenceQueue = new ReferenceQueue();
        imageCleaner = new ReferenceCleanerThread(imageReferenceQueue, imageMap);
        imageCleaner.start();
    }

    /**
     * Constructs a new instance of <code>ImageCache</code>, and starts a
     * thread to monitor the reference queue. If the passed initial load
     * capacity is negative or if the load factor is nonpositive, the image map
     * is created with the default <code>HashMap</code> constructor.
     * 
     * @param initialLoadCapacity
     *            Initial load capacity for the image hash map.
     * @param loadFactor
     *            Load factor for the image hash map.
     */
    public ImageCache(final int initialLoadCapacity, final float loadFactor) {
        if (initialLoadCapacity < 0 || loadFactor <= 0) {
            imageMap = new HashMap();
        } else {
            imageMap = new HashMap(initialLoadCapacity, loadFactor);
        }

        imageReferenceQueue = new ReferenceQueue();
        imageCleaner = new ReferenceCleanerThread(imageReferenceQueue, imageMap);
        imageCleaner.start();
    }

    /**
     * Add a new equivalence set to the imag emap.
     * 
     * @param imageDescriptor
     *            The image descriptor.
     * @param temporaryKey
     *            The temporary key.
     * @param typeOfImage
     *            The type of image requested.
     * @return the requested image, or the missing image if an error occurs in
     *         the creation of the image.
     */
    private Image addNewEquivalenceSet(final ImageDescriptor imageDescriptor,
            EquivalenceSet temporaryKey, int typeOfImage) {
        ImageMapEntry mapEntry;
        Image[] images;

        // Create the array of images, as well as the regular image
        // since it will be need to create gray or disable
        images = new Image[TYPES_OF_IMAGES];
        images[NORMAL] = imageDescriptor.createImage(false);

        // If the image creation fails, returns the missing image
        if (images[NORMAL] == null) {
            // clear the key (this will also clear the reference created)
            temporaryKey.clear();
            return getMissingImage();

        }
        if (typeOfImage == DISABLED) {
            images[typeOfImage] = new Image(null, images[NORMAL],
                    SWT.IMAGE_DISABLE);
        } else if (typeOfImage == GRAY) {
            images[typeOfImage] = new Image(null, images[NORMAL],
                    SWT.IMAGE_GRAY);
        }
        // Add the entry to the map
        mapEntry = new ImageMapEntry(temporaryKey, images);
        imageMap.put(temporaryKey, mapEntry);
        return images[typeOfImage];
    }

    /**
     * Cleans up all images in the cache. This disposes of all of the images,
     * and drops references to them. This should only be called when the images
     * in the image cache are no longer needed (i.e.: on shutdown).
     */
    public final void dispose() {
        // Clean up the missing image.
        if ((missingImage != null) && (!missingImage.isDisposed())) {
            missingImage.dispose();
            missingImage = null;
        }

        // Stop the image cleaner thread
        imageCleaner.stopCleaning();

        // Clear all the references in the equivalence sets and dispose
        // the corresponding images
        final Iterator imageItr = imageMap.entrySet().iterator();
        while (imageItr.hasNext()) {
            final Map.Entry entry = (Map.Entry) imageItr.next();
            final EquivalenceSet key = (EquivalenceSet) entry.getKey();
            // Dispose the images if they have been created and have not been
            // disposed yet
            final Image[] images = ((ImageMapEntry) entry.getValue())
                    .getImages();
            for (int i = 0; i < images.length; i++) {
                final Image image = images[i];
                if ((image != null) && (!image.isDisposed())) {
                    image.dispose();
                }
            }

            // Clear all the references in the equivalence set
            key.clear();
        }
        // Clear map
        imageMap.clear();
    }

    /**
     * Returns the disabled image for the given image descriptor. This caches
     * the result so that future attempts to get the disabled image for an
     * equivalent or identical image descriptor will only access the cache. When
     * all references to equivalent image descriptors are dropped, the images
     * (regular, gray and disabled) will be cleaned up if they have been
     * created. This clean up makes no guarantees about how long or when it will
     * take place. Note that the regular image for the passed image descriptor
     * will be created as well if it is not in the cache.
     * 
     * @param descriptor
     *            The image descriptor for which a disabled image should be
     *            created; may be <code>null</code>.
     * @return The disabled image, either newly created or from the cache. This
     *         value is <code>null</code> if the parameter passed in is
     *         <code>null</code>. Note that a missing image will be returned
     *         if a problem occurs in the creation of the requested image.
     */
    public final Image getDisabledImage(final ImageDescriptor imageDescriptor) {
        return getImage(imageDescriptor, DISABLED);
    }

    /**
     * Returns the gray image for the given image descriptor. This caches the
     * result so that future attempts to get the gray image for an equivalent or
     * identical image descriptor will only access the cache. When all
     * references to equivalent image descriptors are dropped, the images
     * (regular, gray and disabled) will be cleaned up if they have been
     * created. This clean up makes no guarantees about how long or when it will
     * take place. Note that the regular image for the passed image descriptor
     * will be created as well if it is not in the cache.
     * 
     * @param descriptor
     *            The image descriptor for which a gray image should be created;
     *            may be <code>null</code>.
     * @return The gray image, either newly created or from the cache. This
     *         value is <code>null</code> if the parameter passed in is
     *         <code>null</code>. Note that a missing image will be returned
     *         if a problem occurs in the creation of the requested image.
     */
    public final Image getGrayImage(final ImageDescriptor imageDescriptor) {
        return getImage(imageDescriptor, GRAY);
    }

    /**
     * Returns the regular image for the given image descriptor. This caches the
     * result so that future attempts to get the regular image for an equivalent
     * or identical image descriptor will only access the cache. When all
     * references to equivalent image descriptors are dropped, the images
     * (regular, gray and disabled) will be cleaned up if they have been
     * created. This clean up makes no guarantees about how long or when it will
     * take place.
     * 
     * @param descriptor
     *            The image descriptor for which a regular image should be
     *            created; may be <code>null</code>.
     * @return The regular image, either newly created or from the cache. This
     *         value is <code>null</code> if the parameter passed in is
     *         <code>null</code>. Note that a missing image will be returned
     *         if a problem occurs in the creation of the requested image.
     */
    public final Image getImage(final ImageDescriptor imageDescriptor) {
        return getImage(imageDescriptor, NORMAL);
    }

    /**
     * Returns the requested image, either from cache or newly created.
     * 
     * @param imageDescriptor
     *            The image descriptor.
     * @param typeOfImage
     *            The type of image to create.
     * @return the requested image, or a missing image if the image could not be
     *         created.
     */
    private final Image getImage(final ImageDescriptor imageDescriptor,
            int typeOfImage) {
        if (imageDescriptor == null) {
            return null;
        }
        // Created a temporary key to query the image map
        ImageCacheWeakReference referencedToAdd = new ImageCacheWeakReference(
                imageDescriptor, imageReferenceQueue);
        EquivalenceSet temporaryKey = new EquivalenceSet(referencedToAdd);

        // Retrieve the corresponding entry in the map
        ImageMapEntry mapEntry = (ImageMapEntry) imageMap.get(temporaryKey);

        if (mapEntry != null) {
            // The entry was found, retrieve the image from cache, or newly
            // created.
            return getImageFromEquivalenceSet(imageDescriptor, mapEntry,
                    referencedToAdd, typeOfImage);

        }
        // The entry was not found, create it.
        return addNewEquivalenceSet(imageDescriptor, temporaryKey, typeOfImage);
    }

    /**
     * Retrieve the image from the cache, or create it if it has not been
     * created yet.
     * 
     * @param imageDescriptor
     *            The image descriptor.
     * @param mapEntry
     *            The mape entry.
     * @param referenceToAdd
     *            The weak reference to add.
     * @param typeOfImage
     *            The type of image to create.
     * @return the requested image, or the missing image if an error occurs in
     *         the creation of the image.
     */
    private Image getImageFromEquivalenceSet(ImageDescriptor imageDescriptor,
            ImageMapEntry mapEntry, ImageCacheWeakReference referenceToAdd,
            int typeOfImage) {
        // We have found an entry in the map
        Image[] images = mapEntry.getImages();
        // The temporary key is not needed anymore
        EquivalenceSet temporaryKey = mapEntry.getEquivalenceSet();
        // Add the weak reference to the equivalence set
        boolean added = temporaryKey.addWeakReference(referenceToAdd);
        if (!added) {
            // The identical reference already exists in the set, clear it
            referenceToAdd.clear();
        }
        // If the type of image requested is cached
        if (images[typeOfImage] != null) {
            return images[typeOfImage];
        }

        // Regular image shoudl not be null, since it gets created when the set
        // is created
        if (images[NORMAL] == null) {
            throw new NullPointerException(
                    "The normal image from the equivalence set should not be null.");//$NON-NLS-1$
        }

        if (typeOfImage == GRAY) {
            images[typeOfImage] = new Image(null, images[NORMAL],
                    SWT.IMAGE_GRAY);
        } else if (typeOfImage == DISABLED) {
            images[typeOfImage] = new Image(null, images[NORMAL],
                    SWT.IMAGE_DISABLE);
        }
        return images[typeOfImage];
    }

    /**
     * Returns the image to display when no image can be found, or none is
     * specified. This image is only disposed when the cache is disposed.
     * 
     * @return The image to display for missing images. This value will never be
     *         <code>null</code>.
     */
    public final Image getMissingImage() {
        if (missingImage == null) {
            missingImage = ImageDescriptor.getMissingImageDescriptor()
                    .createImage();
        }

        return missingImage;
    }
}