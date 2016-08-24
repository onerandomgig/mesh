package com.gentics.mesh.core.data.node.field;

import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.File;

import com.gentics.mesh.core.data.node.field.impl.BinaryGraphFieldImpl;
import com.gentics.mesh.core.rest.node.field.BinaryField;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import rx.Single;

/**
 * The BinaryField Domain Model interface.
 */
public interface BinaryGraphField extends BasicGraphField<BinaryField> {

	FieldTransformator BINARY_TRANSFORMATOR = (container, ac, fieldKey, fieldSchema, languageTags, level, parentNode) -> {
		BinaryGraphField graphBinaryField = container.getBinary(fieldKey);
		if (graphBinaryField == null) {
			return Single.just(null);
		} else {
			return graphBinaryField.transformToRest(ac);
		}
	};

	FieldUpdater BINARY_UPDATER = (container, ac, fieldMap, fieldKey, fieldSchema, schema) -> {
		container.reload();
		BinaryGraphField graphBinaryField = container.getBinary(fieldKey);
		BinaryField binaryField = fieldMap.getBinaryField(fieldKey);
		boolean isBinaryFieldSetToNull = fieldMap.hasField(fieldKey) && binaryField == null && graphBinaryField != null;
		GraphField.failOnDeletionOfRequiredField(graphBinaryField, isBinaryFieldSetToNull, fieldSchema, fieldKey, schema);
		boolean restIsNull = binaryField == null;
		// The required check for binary fields is not enabled since binary fields can only be created using the field api

		// Handle Deletion
		if (isBinaryFieldSetToNull && graphBinaryField != null) {
			graphBinaryField.removeField(container);
			return;
		}

		// Rest model is empty or null - Abort
		if (restIsNull) {
			return;
		}

		// Always create a new binary field since each update must create a new field instance. The old field must be detached from the given container.
		BinaryGraphField newGraphBinaryField = container.createBinary(fieldKey);

		// Handle Update - Dominant Color
		if (binaryField.getDominantColor() != null) {
			newGraphBinaryField.setImageDominantColor(binaryField.getDominantColor());
		}

		// Handle Update - Filename
		if (binaryField.getFileName() != null) {
			if (isEmpty(binaryField.getFileName())) {
				throw error(BAD_REQUEST, "field_binary_error_emptyfilename", fieldKey);
			} else {
				newGraphBinaryField.setFileName(binaryField.getFileName());
			}
		}

		// Handle Update - MimeType
		if (binaryField.getMimeType() != null) {
			if (isEmpty(binaryField.getMimeType())) {
				throw error(BAD_REQUEST, "field_binary_error_emptymimetype", fieldKey);
			}
			newGraphBinaryField.setMimeType(binaryField.getMimeType());
		}
		// Don't update image width, height, SHA checksum - those are immutable
	};

	FieldGetter BINARY_GETTER = (container, fieldSchema) -> {
		return container.getBinary(fieldSchema.getName());
	};

	/**
	 * Return the binary filename.
	 * 
	 * @return
	 */
	String getFileName();

	/**
	 * Copy the values of this field to the specified target field.
	 * 
	 * @param target
	 * @return Fluent API
	 */
	BinaryGraphField copyTo(BinaryGraphField target);

	/**
	 * Check whether the binary data represents an image.
	 * 
	 * @return
	 */
	boolean hasImage();

	/**
	 * Set the binary filename.
	 * 
	 * @param filenName
	 * @return Fluent API
	 */
	BinaryGraphField setFileName(String filenName);

	/**
	 * Return the binary mime type of the node.
	 * 
	 * @return
	 */
	String getMimeType();

	/**
	 * Set the binary mime type of the node.
	 * 
	 * @param mimeType
	 * @return Fluent API
	 */
	BinaryGraphField setMimeType(String mimeType);

	/**
	 * Return future that holds a buffer reference to the binary file data.
	 * 
	 * @return
	 */
	Future<Buffer> getFileBuffer();

	/**
	 * Return the file that points to the binary file within the binary file storage.
	 * 
	 * @return Found file or null when no binary file could be found
	 */
	File getFile();

	/**
	 * Set the binary file size in bytes
	 * 
	 * @param sizeInBytes
	 * @return Fluent API
	 */
	BinaryGraphField setFileSize(long sizeInBytes);

	/**
	 * Return the binary file size in bytes
	 * 
	 * @return
	 */
	long getFileSize();

	/**
	 * Set the binary SHA 512 checksum.
	 * 
	 * @param sha512HashSum
	 * @return Fluent API
	 */
	BinaryGraphField setSHA512Sum(String sha512HashSum);

	/**
	 * Return the binary SHA 512 checksum.
	 * 
	 * @return
	 */
	String getSHA512Sum();

	/**
	 * Set the binary image dominant color.
	 * 
	 * @param dominantColor
	 */
	void setImageDominantColor(String dominantColor);

	/**
	 * Return the binary image dominant color.
	 * 
	 * @return
	 */
	String getImageDominantColor();

	/**
	 * Return the binary image height.
	 * 
	 * @return
	 */
	Integer getImageHeight();

	/**
	 * Set the image width of the binary image.
	 * 
	 * @param width
	 * @return Fluent API
	 */
	BinaryGraphField setImageWidth(Integer width);

	/**
	 * Return the width of the binary image.
	 * 
	 * @return
	 */
	Integer getImageWidth();

	/**
	 * Set the with of the binary image. You can set this null to indicate that the binary data has no height.
	 * 
	 * @param heigth
	 * @return Fluent API
	 */
	BinaryGraphField setImageHeight(Integer heigth);

	/**
	 * Returns the segmented path that points to the binary file within the binary file location. The segmented path is build using the uuid of the binary field
	 * vertex.
	 * 
	 * @return
	 */
	String getSegmentedPath();

	/**
	 * Return the file path for the binary file location of the node.
	 * 
	 * @return
	 */
	String getFilePath();

	/**
	 * Return the uuid of the binary field.
	 * 
	 * @return
	 */
	String getUuid();

	/**
	 * Set the uuid of the binary field.
	 * 
	 * @param uuid
	 */
	void setUuid(String uuid);

	BinaryGraphFieldImpl getImpl();

}