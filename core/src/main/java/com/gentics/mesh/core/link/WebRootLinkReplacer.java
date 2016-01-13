package com.gentics.mesh.core.link;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.root.MeshRoot;
import com.gentics.mesh.etc.RouterStorage;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import rx.Observable;

/**
 * This class will resolve mesh link placeholders.
 */
@Component
public class WebRootLinkReplacer {

	private static final String START_TAG = "{{mesh.link(";
	private static final String END_TAG = ")}}";

	private static WebRootLinkReplacer instance;

	private static final Logger log = LoggerFactory.getLogger(WebRootLinkReplacer.class);

	@PostConstruct
	public void setup() {
		WebRootLinkReplacer.instance = this;
	}

	public static WebRootLinkReplacer getInstance() {
		return instance;
	}

	/**
	 * Replace the links in the content.
	 * @param content content containing links to replace
	 * @param type replacing type
	 * 
	 * @return content with links (probably) replaced
	 */
	public String replace(String content, Type type) {
		if (isEmpty(content) || type == Type.OFF || type == null) {
			return content;
		}

		List<Observable<String>> segments = new ArrayList<>();
		int pos = 0;
		int lastPos = 0;
		int length = content.length();

		// 1. Tokenize the content
		while (lastPos < length) {
			pos = content.indexOf(START_TAG, lastPos);
			if (pos == -1) {
				// add last string segment
				if (lastPos < length) {
					segments.add(Observable.just(content.substring(lastPos)));
				}
				break;
			}
			int endPos = content.indexOf(END_TAG, pos);
			if (endPos == -1) {
				// add last string segment
				if (lastPos < length) {
					segments.add(Observable.just(content.substring(lastPos)));
				}
				break;
			}

			// add intermediate string segment
			if (lastPos < pos) {
				segments.add(Observable.just(content.substring(lastPos, pos)));
			}

			// 2. Parse the link and invoke resolving
			String link = content.substring(pos + START_TAG.length(), endPos);
			// Strip away the quotes. We only care about the argument values
			// double quotes may be escaped
			link = link.replaceAll("\\\\\"", "");
			link = link.replaceAll("'", "");
			link = link.replaceAll("\"", "");
			String[] linkArguments = link.split(",");
			if (linkArguments.length == 2) {
				segments.add(resolve(linkArguments[0], linkArguments[1], type));
			} else {
				segments.add(resolve(linkArguments[0], null, type));
			}

			lastPos = endPos + END_TAG.length();
		}

		// 3.: Buildup the new content
		StringBuilder renderedContent = new StringBuilder(length);
		segments.stream().forEachOrdered(obs -> renderedContent.append(obs.toBlocking().first()));

		return renderedContent.toString();
	}

	/**
	 * Resolve the link to the node with uuid (in the given language) into an observable
	 * @param uuid target uuid
	 * @param languageTag optional language
	 * @param type link type
	 * @return observable of the rendered link
	 */
	public Observable<String> resolve(String uuid, String languageTag, Type type) {
		// Get rid of additional whitespaces
		uuid = uuid.trim();
		Node node = MeshRoot.getInstance().getNodeRoot().findByUuid(uuid).toBlocking().single();

		// check for null
		if (node == null) {
			if (log.isDebugEnabled()) {
				log.debug("Could not resolve link to '" + uuid + "', target node could not be found");
			}
			return Observable.just("#");
		}
		return resolve(node, languageTag, type);
	}

	/**
	 * Resolve the link to the given node
	 * @param node target node
	 * @param languageTag optional language
	 * @param type link type
	 * @return observable of the rendered link
	 */
	public Observable<String> resolve(Node node, String languageTag, Type type) {
		if (languageTag == null) {
			languageTag = Mesh.mesh().getOptions().getDefaultLanguage();
			if (log.isDebugEnabled()) {
				log.debug("Fallback to default language " + languageTag);
			}
		}
		languageTag = languageTag.trim();
		return resolve(node, MeshRoot.getInstance().getLanguageRoot().findByLanguageTag(languageTag), type);
	}

	/**
	 * Resolve the link to the given node
	 * @param node target node
	 * @param language target language
	 * @param type link type
	 * @return observable of the rendered link
	 */
	public Observable<String> resolve(Node node, Language language, Type type) {
		if (language == null) {
			String defaultLanguageTag = Mesh.mesh().getOptions().getDefaultLanguage();
			language = MeshRoot.getInstance().getLanguageRoot().findByLanguageTag(defaultLanguageTag);
			if (language == null) {
				return Observable.error(new Exception("Could not find default language " + defaultLanguageTag));
			}
			if (log.isDebugEnabled()) {
				log.debug("Fallback to default language " + defaultLanguageTag);
			}
		}
		try {
			if (log.isDebugEnabled()) {
				log.debug("Resolving link to " + node.getUuid() + " in language " + language.getLanguageTag() + " with type " + type);
			}
			switch (type) {
			case SHORT:
				return node.getPath(language);
			case MEDIUM:
				return node.getPath(language).map(path -> "/" + node.getProject().getName() + path);
			case FULL:
				return node.getPath(language).map(path -> RouterStorage.DEFAULT_API_MOUNTPOINT + "/" + node.getProject().getName() + "/webroot" + path);
			default:
				return Observable.error(new Exception("Cannot render link with type " + type));
			}
		} catch (UnsupportedEncodingException e) {
			return Observable.error(e);
		}
	}

	/**
	 * Link Replacing type
	 */
	public static enum Type {
		/**
		 * No link replacing
		 */
		OFF,

		/**
		 * Link replacing without the API prefix and without the project name
		 */
		SHORT,

		/**
		 * Link replacing without the API prefix, but with the project name
		 */
		MEDIUM,

		/**
		 * Link replacing with API prefix and project name
		 */
		FULL
	}
}
