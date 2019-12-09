package io.mosip.idrepository.identity.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;

import io.mosip.idrepository.core.constant.IdRepoConstants;
import io.mosip.idrepository.core.constant.IdRepoErrorConstants;
import io.mosip.idrepository.core.dto.IdRequestDTO;
import io.mosip.idrepository.core.dto.IdResponseDTO;
import io.mosip.idrepository.core.exception.IdRepoAppException;
import io.mosip.idrepository.core.exception.IdRepoDataValidationException;
import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.core.security.IdRepoSecurityManager;
import io.mosip.idrepository.core.spi.IdRepoService;
import io.mosip.idrepository.core.util.DataValidationUtil;
import io.mosip.idrepository.identity.validator.IdRequestValidator;
import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.StringUtils;
import springfox.documentation.annotations.ApiIgnore;

/**
 * The Class IdRepoController - Controller class for Identity service.
 * These services is used by Registration Processor to store/update during 
 * registration process and ID Authentication to retrieve Identity of an 
 * Individual for their authentication.
 *
 * @author Manoj SP
 */
@RestController
public class IdRepoController {

	/** The Constant READ. */
	private static final String READ = "read";

	/** The Constant REGISTRATION_ID. */
	private static final String REGISTRATION_ID = "registrationId";

	/** The Constant RETRIEVE_IDENTITY_BY_RID. */
	private static final String RETRIEVE_IDENTITY_BY_RID = "retrieveIdentityByRid";

	/** The Constant RETRIEVE_IDENTITY. */
	private static final String RETRIEVE_IDENTITY = "retrieveIdentity";

	/** The Constant ALL. */
	private static final String ALL = "all";

	/** The Constant CHECK_TYPE. */
	private static final String CHECK_TYPE = "checkType";

	/** The mosip logger. */
	Logger mosipLogger = IdRepoLogger.getLogger(IdRepoController.class);

	/** The Constant CREATE. */
	private static final String CREATE = "create";

	/** The Constant CREATE. */
	private static final String UPDATE = "update";

	/** The Constant TYPE. */
	private static final String TYPE = "type";

	/** The Constant ID_REPO_CONTROLLER. */
	private static final String ID_REPO_CONTROLLER = "IdRepoController";

	/** The Constant ADD_IDENTITY. */
	private static final String ADD_IDENTITY = "addIdentity";

	/** The Constant UPDATE_IDENTITY. */
	private static final String UPDATE_IDENTITY = "updateIdentity";

	/** The id. */
	@Resource
	private Map<String, String> id;

	/** The allowed types. */
	@Resource
	private List<String> allowedTypes;

	/** The id repo service. */
	@Autowired
	private IdRepoService<IdRequestDTO, IdResponseDTO> idRepoService;

	/** The validator. */
	@Autowired
	private IdRequestValidator validator;

	/** The mapper. */
	@Autowired
	private ObjectMapper mapper;

	/** The env. */
	@Autowired
	Environment env;

	/**
	 * Inits the binder.
	 *
	 * @param binder the binder
	 */
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(validator);
	}

	/**
	 * This service will create a new ID record in ID repository and store corresponding demographic 
	 * and bio-metric documents.
	 *
	 * @param request the request
	 * @param errors  the errors
	 * @return the response entity
	 * @throws IdRepoAppException the id repo app exception
	 */
	@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PostMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> addIdentity(@Validated @RequestBody IdRequestDTO request,
			@ApiIgnore Errors errors) throws IdRepoAppException {
		try {
			String uin = getUin(request.getRequest());
			validator.validateId(request.getId(), CREATE);
			DataValidationUtil.validate(errors);
			validator.validateUin(uin, CREATE);
			return new ResponseEntity<>(idRepoService.addIdentity(request, uin), HttpStatus.OK);
		} catch (IdRepoDataValidationException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, ADD_IDENTITY, e.getMessage());
			throw new IdRepoAppException(IdRepoErrorConstants.DATA_VALIDATION_FAILED, e);
		} catch (IdRepoAppException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, RETRIEVE_IDENTITY, e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		}
	}

	/**
	 * This service will retrieve an ID record from ID repository for a given UIN and identity type as bio/demo/all.
	 *
	 * @param uin  the uin
	 * @param type the type
	 *
	 * @return the response entity
	 * @throws IdRepoAppException the id repo app exception
	 */
	@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR', 'ID_AUTHENTICATION','RESIDENT')")
	@GetMapping(path = "/uin/{uin}", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> retrieveIdentityByUin(@PathVariable String uin,
			@RequestParam(name = TYPE, required = false) @Nullable String type) throws IdRepoAppException {
		try {
			type = validateType(type);
			validator.validateUin(uin,READ);
			return new ResponseEntity<>(idRepoService.retrieveIdentityByUin(uin, type), HttpStatus.OK);
		} catch (IdRepoAppException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, RETRIEVE_IDENTITY, e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		}
	}

	/**
	 * This operation will retrieve an ID record from ID repository for a given RID and identity type as bio/demo/all.
	 *
	 * @param rid the rid
	 * @param type the type
	 * @return the response entity
	 * @throws IdRepoAppException the id repo app exception
	 */
	@PreAuthorize("hasAnyRole('REGISTRATION_ADMIN','REGISTRATION_SUPERVISOR','REGISTRATION_OFFICER','REGISTRATION_PROCESSOR','ID_AUTHENTICATION','RESIDENT')")
	@GetMapping(path = "/rid/{rid}", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> retrieveIdentityByRid(@PathVariable String rid,
			@RequestParam(name = TYPE, required = false) @Nullable String type) throws IdRepoAppException {
		try {
			type = validateType(type);
			validator.validateRid(rid);
			return new ResponseEntity<>(idRepoService.retrieveIdentityByRid(rid, type), HttpStatus.OK);
		} catch (InvalidIDException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, RETRIEVE_IDENTITY_BY_RID, e.getMessage());
			throw new IdRepoAppException(IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorCode(),
					String.format(IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorMessage(), REGISTRATION_ID));
		} catch (IdRepoAppException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, RETRIEVE_IDENTITY_BY_RID, e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		}
	}

	/**
	 * This operation will update an existing ID record in the ID repository for a given UIN.
	 *
	 * @param request the request
	 * @param errors  the errors
	 * @return the response entity
	 * @throws IdRepoAppException the id repo app exception
	 */
	@PreAuthorize("hasAnyRole('REGISTRATION_PROCESSOR')")
	@PatchMapping(path = "/", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IdResponseDTO> updateIdentity(@Validated @RequestBody IdRequestDTO request,
			@ApiIgnore Errors errors) throws IdRepoAppException {
		try {
			String uin = getUin(request.getRequest());
			validator.validateId(request.getId(), UPDATE);
			DataValidationUtil.validate(errors);
			validator.validateUin(uin, UPDATE);
			return new ResponseEntity<>(idRepoService.updateIdentity(request, uin), HttpStatus.OK);
		} catch (IdRepoDataValidationException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, UPDATE_IDENTITY, e.getMessage());
			throw new IdRepoAppException(IdRepoErrorConstants.DATA_VALIDATION_FAILED, e);
		} catch (IdRepoAppException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, RETRIEVE_IDENTITY, e.getMessage());
			throw new IdRepoAppException(e.getErrorCode(), e.getErrorText(), e);
		}
	}

	/**
	 * Validate type query parameter.
	 *
	 * @param type the type
	 * @return the string
	 * @throws IdRepoAppException the id repo app exception
	 */
	private String validateType(String type) throws IdRepoAppException {
		if (Objects.nonNull(type)) {
			List<String> typeList = Arrays.asList(StringUtils.split(type.toLowerCase(), ','));
			if (typeList.size() == 1 && !allowedTypes.containsAll(typeList)) {
				mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, CHECK_TYPE,
						IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorMessage() + typeList);
				throw new IdRepoAppException(IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorCode(),
						String.format(IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorMessage(), TYPE));
			} else {
				typeList.contains(allowedTypes.get(0));
				if (typeList.contains(ALL) || allowedTypes.parallelStream()
						.filter(allowedType -> !allowedType.equals(ALL)).allMatch(typeList::contains)) {
					type = ALL;
				} else if (!allowedTypes.containsAll(typeList)) {
					mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, CHECK_TYPE,
							IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorMessage() + typeList);
					throw new IdRepoAppException(IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorCode(),
							String.format(IdRepoErrorConstants.INVALID_INPUT_PARAMETER.getErrorMessage(), TYPE));
				}
			}
		}
		return type;
	}

	/**
	 * This Method returns Uin from the Identity Object.
	 *
	 * @param request the request
	 * @return the uin
	 * @throws IdRepoAppException the id repo app exception
	 */
	private String getUin(Object request) throws IdRepoAppException {
		Object uin = null;
		String pathOfUin = env.getProperty(IdRepoConstants.MOSIP_KERNEL_IDREPO_JSON_PATH.getValue());
		try {
			String identity = mapper.writeValueAsString(request);
			JsonPath jsonPath = JsonPath.compile(pathOfUin);
			uin = jsonPath.read(identity);
			return String.valueOf(uin);
		} catch (JsonProcessingException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, "getUin", e.getMessage());
			throw new IdRepoAppException(IdRepoErrorConstants.INVALID_REQUEST, e);
		} catch (JsonPathException e) {
			mosipLogger.error(IdRepoSecurityManager.getUser(), ID_REPO_CONTROLLER, "getUin", e.getMessage());
			throw new IdRepoAppException(IdRepoErrorConstants.MISSING_INPUT_PARAMETER.getErrorCode(), String.format(
					IdRepoErrorConstants.MISSING_INPUT_PARAMETER.getErrorMessage(), "/" + pathOfUin.replace(".", "/")));
		}
	}
}
