package com.liferay.custom.Captcha;

import com.liferay.captcha.configuration.CaptchaConfiguration;
import com.liferay.captcha.simplecaptcha.SimpleCaptchaImpl;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import nl.captcha.Captcha;

import com.liferay.portal.kernel.captcha.CaptchaException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;

import javax.portlet.Portlet;
import javax.portlet.PortletRequest;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.liferay.portal.kernel.security.RandomUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import nl.captcha.backgrounds.BackgroundProducer;
import nl.captcha.gimpy.GimpyRenderer;
import nl.captcha.noise.NoiseProducer;
import nl.captcha.servlet.CaptchaServletUtil;
import nl.captcha.text.producer.DefaultTextProducer;
import nl.captcha.text.producer.TextProducer;
import nl.captcha.text.renderer.WordRenderer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author siddhantjain
 */

@Component(
		configurationPid = "com.liferay.captcha.configuration.CaptchaConfiguration",
		property = {"captcha.engine.impl=com.liferay.captcha.simplecaptcha.SimpleCaptchaImpl","service.ranking:Integer=100"},
		service = com.liferay.portal.kernel.captcha.Captcha.class
)
public class CustomSimpleCaptchaImpl implements com.liferay.portal.kernel.captcha.Captcha {
	@Reference
	protected Portal portal;
	@Reference(
			target = "(component.name=com.liferay.captcha.simplecaptcha.SimpleCaptchaImpl)"
	)
	private com.liferay.portal.kernel.captcha.Captcha _defaultService;

	@Override
	public void check(HttpServletRequest httpServletRequest) throws CaptchaException {
		_defaultService.check(httpServletRequest);
	}

	@Override
	public void check(PortletRequest portletRequest) throws CaptchaException {
		_defaultService.check(portletRequest);
	}

	@Override
	public String getTaglibPath() {
		return _defaultService.getTaglibPath();
	}

	@Override
	public boolean isEnabled(HttpServletRequest httpServletRequest) {
		if (isExceededMaxChallenges(httpServletRequest)) {
			return false;
		}

		if (_captchaConfiguration.maxChallenges() >= 0) {
			return true;
		}

		return false;

	}
	protected boolean isExceededMaxChallenges(
			HttpServletRequest httpServletRequest) {

		if (_captchaConfiguration.maxChallenges() > 0) {
			HttpSession httpSession = _getHttpSession(httpServletRequest);

			Integer count = (Integer)httpSession.getAttribute(
					_getHttpSessionKey(WebKeys.CAPTCHA_COUNT, httpServletRequest));

			return isExceededMaxChallenges(count);
		}

		return false;
	}
	private String _getHttpSessionKey(
			String key, HttpServletRequest httpServletRequest) {

		String portletId = portal.getPortletId(httpServletRequest);

		if (Validator.isNotNull(portletId)) {
			return portal.getPortletNamespace(portletId) + key;
		}

		return key;
	}


	protected boolean isExceededMaxChallenges(Integer count) {
		if ((count != null) &&
				(count >= _captchaConfiguration.maxChallenges())) {

			return true;
		}

		return false;
	}

	protected boolean isExceededMaxChallenges(PortletRequest portletRequest) {
		return isExceededMaxChallenges(
				portal.getHttpServletRequest(portletRequest));
	}

	protected void setCaptchaConfiguration(
			CaptchaConfiguration captchaConfiguration) {

		_captchaConfiguration = captchaConfiguration;
	}


	@Override
	public boolean isEnabled(PortletRequest portletRequest) {
		return _defaultService.isEnabled(portletRequest);
	}

	@Override
	public void serveImage(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
		HttpSession httpSession = _getHttpSession(httpServletRequest);
		String key = "CAPTCHA_TEXT";
		String portletId = ParamUtil.getString(httpServletRequest, "portletId");
		if (Validator.isNotNull(portletId))
			key = this.portal.getPortletNamespace(portletId) + key;
		Captcha simpleCaptcha = getSimpleCaptcha();
		httpSession.setAttribute(key, simpleCaptcha.getAnswer());
		httpServletResponse.setContentType("image/png");
		CaptchaServletUtil.writeImage((OutputStream)httpServletResponse
				.getOutputStream(), simpleCaptcha.getImage());
	}

	@Override
	public void serveImage(ResourceRequest resourceRequest, ResourceResponse resourceResponse) throws IOException {
		_defaultService.serveImage(resourceRequest,resourceResponse);
	}

	private HttpSession _getHttpSession(HttpServletRequest httpServletRequest) {
		HttpServletRequest originalHttpServletRequest = this.portal.getOriginalServletRequest(httpServletRequest);
		return originalHttpServletRequest.getSession();
	}

	protected Captcha getSimpleCaptcha() {
		Captcha.Builder captchaBuilder = new Captcha.Builder(getWidth(), getHeight());
		//please read the text producer from config and call with param as no. of chars needed
		//**************this is where the change is needed
		captchaBuilder.addText(new DefaultTextProducer(6), getWordRenderer());
		captchaBuilder.addBackground(getBackgroundProducer());
		captchaBuilder.gimp(getGimpyRenderer());
		captchaBuilder.addNoise(getNoiseProducer());
		captchaBuilder.addBorder();
		return captchaBuilder.build();
	}
	protected BackgroundProducer getBackgroundProducer() {
		if (_backgroundProducers.length == 1) {
			return _backgroundProducers[0];
		}

		int pos = RandomUtil.nextInt(_backgroundProducers.length);

		return _backgroundProducers[pos];
	}

	protected GimpyRenderer getGimpyRenderer() {
		if (_gimpyRenderers.length == 1) {
			return _gimpyRenderers[0];
		}

		int pos = RandomUtil.nextInt(_gimpyRenderers.length);

		return _gimpyRenderers[pos];
	}

	protected int getHeight() {
		return _captchaConfiguration.simpleCaptchaHeight();
	}

	protected NoiseProducer getNoiseProducer() {
		if (_noiseProducers.length == 1) {
			return _noiseProducers[0];
		}

		int pos = RandomUtil.nextInt(_noiseProducers.length);

		return _noiseProducers[pos];
	}
	protected TextProducer getTextProducer() {
		if (_textProducers.length == 1) {
			return _textProducers[0];
		}

		int pos = RandomUtil.nextInt(_textProducers.length);

		return _textProducers[pos];
	}

	protected int getWidth() {
		return _captchaConfiguration.simpleCaptchaWidth();
	}
	protected void activate() {
		initBackgroundProducers();
		initGimpyRenderers();
		initNoiseProducers();
		initTextProducers();
		initWordRenderers();
	}

	@Activate
	@Modified
	protected void activate(Map<String, Object> properties) {
		_captchaConfiguration = ConfigurableUtil.createConfigurable(
				CaptchaConfiguration.class, properties);

		activate();
	}
	protected void initBackgroundProducers() {
		String[] backgroundProducerClassNames =
				_captchaConfiguration.simpleCaptchaBackgroundProducers();

		_backgroundProducers =
				new BackgroundProducer[backgroundProducerClassNames.length];

		for (int i = 0; i < backgroundProducerClassNames.length; i++) {
			String backgroundProducerClassName =
					backgroundProducerClassNames[i];

			_backgroundProducers[i] = (BackgroundProducer)_getInstance(
					backgroundProducerClassName);
		}
	}

	protected void initGimpyRenderers() {
		String[] gimpyRendererClassNames =
				_captchaConfiguration.simpleCaptchaGimpyRenderers();

		_gimpyRenderers = new GimpyRenderer[gimpyRendererClassNames.length];

		for (int i = 0; i < gimpyRendererClassNames.length; i++) {
			String gimpyRendererClassName = gimpyRendererClassNames[i];

			_gimpyRenderers[i] = (GimpyRenderer)_getInstance(
					gimpyRendererClassName);
		}
	}
	private Object _getInstance(String className) {
		className = className.trim();

		Object instance = _instances.get(className);

		if (instance != null) {
			return instance;
		}

		try {
			Class<?> clazz = _loadClass(className);

			instance = clazz.newInstance();

			_instances.put(className, instance);
		}
		catch (Exception exception) {
			_log.error("Unable to load " + className, exception);
		}

		return instance;
	}
	private Class<?> _loadClass(String className) throws Exception {
		Class<?> clazz = getClass();

		ClassLoader classLoader = clazz.getClassLoader();

		return classLoader.loadClass(className);
	}

	protected void initNoiseProducers() {
		String[] noiseProducerClassNames =
				_captchaConfiguration.simpleCaptchaNoiseProducers();

		_noiseProducers = new NoiseProducer[noiseProducerClassNames.length];

		for (int i = 0; i < noiseProducerClassNames.length; i++) {
			String noiseProducerClassName = noiseProducerClassNames[i];

			_noiseProducers[i] = (NoiseProducer)_getInstance(
					noiseProducerClassName);
		}
	}

	protected void initTextProducers() {
		String[] textProducerClassNames =
				_captchaConfiguration.simpleCaptchaTextProducers();

		_textProducers = new TextProducer[textProducerClassNames.length];

		for (int i = 0; i < textProducerClassNames.length; i++) {
			String textProducerClassName = textProducerClassNames[i];

			_textProducers[i] = (TextProducer)_getInstance(
					textProducerClassName);
		}
	}

	protected void initWordRenderers() {
		String[] wordRendererClassNames =
				_captchaConfiguration.simpleCaptchaWordRenderers();

		_wordRenderers = new WordRenderer[wordRendererClassNames.length];

		for (int i = 0; i < wordRendererClassNames.length; i++) {
			String wordRendererClassName = wordRendererClassNames[i];

			_wordRenderers[i] = (WordRenderer)_getInstance(
					wordRendererClassName);
		}
	}

	protected WordRenderer getWordRenderer() {
		if (_wordRenderers.length == 1) {
			return _wordRenderers[0];
		}

		int pos = RandomUtil.nextInt(_wordRenderers.length);

		return _wordRenderers[pos];
	}
	private BackgroundProducer[] _backgroundProducers;
	private volatile CaptchaConfiguration _captchaConfiguration;
	private GimpyRenderer[] _gimpyRenderers;
	private final Map<String, Object> _instances = new ConcurrentHashMap<>();
	private NoiseProducer[] _noiseProducers;
	private TextProducer[] _textProducers;
	private WordRenderer[] _wordRenderers;
	private static final Log _log = LogFactoryUtil.getLog(
			SimpleCaptchaImpl.class);
}
