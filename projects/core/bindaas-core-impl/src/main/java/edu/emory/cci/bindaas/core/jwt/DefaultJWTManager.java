package edu.emory.cci.bindaas.core.jwt;

import java.security.SecureRandom;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;

import edu.emory.cci.bindaas.core.jwt.JWTManagerException.Reason;
import edu.emory.cci.bindaas.core.model.hibernate.HistoryLog;
import edu.emory.cci.bindaas.core.model.hibernate.HistoryLog.ActivityType;
import edu.emory.cci.bindaas.core.model.hibernate.UserRequest;
import edu.emory.cci.bindaas.core.model.hibernate.UserRequest.Stage;
import edu.emory.cci.bindaas.security.api.BindaasUser;

import static edu.emory.cci.bindaas.core.rest.security.SecurityHandler.invalidateJWT;

public class DefaultJWTManager implements IJWTManager {

	private Log log = LogFactory.getLog(getClass());
	private SessionFactory sessionFactory;
	
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	private final static String secret = "fj32Jfv02Mq33g0f8ioDkw";
	// FIXME: discuss secret generation
	//  SecretKey secretKey = KeyGenerator.getInstance("HMACSHA256").generateKey();


	@Override
	public String generateJWT(BindaasUser bindaasUser , Date dateExpires, String initiatedBy , String comments , ActivityType activityType , boolean throwErrorIfAlreadyExists)
			throws JWTManagerException {
		Session session = sessionFactory.openSession();

		try {
			session.beginTransaction();

			String emailAddress = bindaasUser
					.getProperty(BindaasUser.EMAIL_ADDRESS) != null ? bindaasUser
					.getProperty(BindaasUser.EMAIL_ADDRESS).toString()
					: bindaasUser.getName() + "@" + bindaasUser.getDomain();
			String firstName = bindaasUser
					.getProperty(BindaasUser.FIRST_NAME) != null ? bindaasUser
					.getProperty(BindaasUser.FIRST_NAME).toString()
					: bindaasUser.getName();
			String lastName = bindaasUser.getProperty(BindaasUser.LAST_NAME) != null ? bindaasUser
					.getProperty(BindaasUser.LAST_NAME).toString()
					: bindaasUser.getName();

			// default user role if none is specified
			String userRole = bindaasUser.getProperty(BindaasUser.ROLE) !=null ?
					bindaasUser.getProperty(BindaasUser.ROLE).toString()
					:"read-only";

			@SuppressWarnings("unchecked")
			List<UserRequest> listOfValidTokens = (List<UserRequest>) session
					.createCriteria(UserRequest.class)
					.add(Restrictions.eq("stage", Stage.accepted.name()))
					.add(Restrictions.eq("emailAddress", emailAddress))
					.add(Restrictions.gt("dateExpires", new Date()))
					.add(Restrictions.isNotNull("jwt"))
					.list();

			if (listOfValidTokens != null && listOfValidTokens.size() > 0) {
				UserRequest request = listOfValidTokens.get(0);
				if(throwErrorIfAlreadyExists)
				{
					throw new JWTManagerException("JWT for the user already exists" , Reason.TOKEN_ALREADY_EXIST);
				}
				else
				{
					return request.getJWT();
				}
			}


			// generates JWT for first time user
			String jws = JWT.create()
					.withIssuer("bindaas")
					.withExpiresAt(dateExpires)
					.withAudience(userRole)  //FIXME: custom claim?
					.sign(Algorithm.HMAC256(secret));

			UserRequest userRequest = new UserRequest();
			userRequest.setStage(Stage.accepted);
			userRequest.setJWT(jws);
			userRequest.setDateExpires(dateExpires);

			userRequest.setEmailAddress(emailAddress);
			userRequest.setFirstName(firstName);
			userRequest.setLastName(lastName);

			session.save(userRequest);
			HistoryLog historyLog = new HistoryLog();
			historyLog.setActivityType(activityType.toString());
			historyLog.setComments(comments);
			historyLog.setInitiatedBy(initiatedBy);
			historyLog.setUserRequest(userRequest);

			session.save(historyLog);
			session.getTransaction().commit();
			return jws;
		}

		catch (JWTManagerException e) { throw e ;}
		catch (Exception e) {
			log.error(e);
			session.getTransaction().rollback();
			throw new JWTManagerException(e,Reason.PROCESSING_ERROR);
		} finally {
			session.close();
		}

	}

	@Override
	public String createShortLivedJWT(BindaasUser bindaasUser, int lifetime , String applicationId) throws JWTManagerException {
		Session session = sessionFactory.openSession();
		try {
			session.beginTransaction();

			String emailAddress = bindaasUser
					.getProperty(BindaasUser.EMAIL_ADDRESS) != null ? bindaasUser
					.getProperty(BindaasUser.EMAIL_ADDRESS).toString()
					: bindaasUser.getName() + "@" + bindaasUser.getDomain();


			@SuppressWarnings("unchecked")
			List<UserRequest> listOfValidTokens = (List<UserRequest>) session
					.createCriteria(UserRequest.class)
					.add(Restrictions.eq("stage", Stage.accepted.name()))
					.add(Restrictions.eq("emailAddress", emailAddress))
					.add(Restrictions.gt("dateExpires", new Date()))
					.add(Restrictions.isNotNull("jwt"))
					.list();

			if (listOfValidTokens != null && listOfValidTokens.size() > 0) {
				UserRequest request = listOfValidTokens.get(0);

				// generate a short lived JWT for this user
				GregorianCalendar calendar = new GregorianCalendar();
				calendar.add(Calendar.SECOND , lifetime);

				Date newExpirationDate = request.getDateExpires().before(calendar.getTime()) ? request.getDateExpires() : calendar.getTime();

				String jws = JWT.create()
						.withIssuer("bindaas")
						.withExpiresAt(newExpirationDate)
						.sign(Algorithm.HMAC256(secret));

				UserRequest sessionKey = new UserRequest();

				sessionKey.setJWT(jws);
				sessionKey.setFirstName(request.getFirstName());
				sessionKey.setLastName(request.getLastName());
				sessionKey.setEmailAddress(emailAddress);
				sessionKey.setReason(request.getReason());
				sessionKey.setRequestDate(new Date());
				sessionKey.setStage(Stage.accepted);
				sessionKey.setDateExpires(newExpirationDate);

				session.save(sessionKey);

				HistoryLog historyLog = new HistoryLog();
				historyLog.setActivityType(ActivityType.SYSTEM_APPROVE);
				historyLog.setComments("Short-Lived JWT");
				historyLog.setInitiatedBy(applicationId);
				historyLog.setUserRequest(sessionKey);

				session.save(historyLog);
				session.getTransaction().commit();

				return jws;

			} else {
				throw new JWTManagerException("Cannot generate JWT for [" + bindaasUser + "]. The User must apply for it first!!" , Reason.TOKEN_DOES_NOT_EXIST);
			}

		}
		catch (JWTManagerException e) {
			throw e;
		}
		catch (Exception e) {
			log.error(e);
			session.getTransaction().rollback();
			throw new JWTManagerException(e, Reason.PROCESSING_ERROR);
		} finally {
			session.close();
		}
	}

	@Override
	public String modifyJWT(Long id, Stage stage, Date dateExpires,
							   String initiatedBy, String comments, ActivityType activityType)
			throws JWTManagerException {
		Session session = sessionFactory.openSession();

		try {
			session.beginTransaction();
			@SuppressWarnings("unchecked")
			List<UserRequest> list = session.createCriteria(UserRequest.class)
					.add(Restrictions.eq("id", id)).list();
			if (list != null && list.size() > 0) {
				UserRequest userRequest = list.get(0);
				HistoryLog historyLog = new HistoryLog();
				historyLog.setComments(comments);
				historyLog.setInitiatedBy(initiatedBy);
				historyLog.setUserRequest(userRequest);
				historyLog.setActivityType(activityType);

				userRequest.setStage(stage);

				switch (activityType) {
					case APPROVE:
					case REFRESH:
						invalidateJWT(userRequest.getJWT()); // remove old JWT from cache
						String jws = JWT.create()
								.withIssuer("bindaas")
								.withExpiresAt(dateExpires)
								.sign(Algorithm.HMAC256(secret));
						userRequest.setJWT(jws);             // overwrite old JWT
						userRequest.setDateExpires(dateExpires);

					case REVOKE:
					case DENY:
					default:
						userRequest.setStage(stage);

				}

				session.save(userRequest);
				session.save(historyLog);
				session.getTransaction().commit();
				return userRequest.getJWT();
			} else {
				throw new Exception("No results found matching id = [" + id
						+ "]");
			}

		} catch (Exception e) {
			session.getTransaction().rollback();
			log.error(e);
			throw new JWTManagerException(e , Reason.PROCESSING_ERROR);
		} finally {
			session.close();
		}

	}

	@Override
	public BindaasUser lookupUser(String jws) throws JWTManagerException {
		Session session = sessionFactory.openSession();
		try {
			// will check for exp claim automatically
			Algorithm algorithm = Algorithm.HMAC256(secret);
			JWTVerifier verifier = JWT.require(algorithm)
					.withIssuer("bindaas")
					.build();

			verifier.verify(jws);

			@SuppressWarnings("unchecked")
			List<UserRequest> listOfValidTokens = (List<UserRequest>) session.createCriteria(UserRequest.class).
					add(Restrictions.eq("stage", Stage.accepted.name())).
					add(Restrictions.eq("jwt", jws)).
					list();

			if(listOfValidTokens!=null && listOfValidTokens.size() > 0)
			{
				UserRequest request = listOfValidTokens.get(0);
				BindaasUser bindaasUser = new BindaasUser(request.getEmailAddress());
				bindaasUser.addProperty(BindaasUser.EMAIL_ADDRESS, request.getEmailAddress());
				bindaasUser.addProperty(BindaasUser.FIRST_NAME, request.getFirstName());
				bindaasUser.addProperty(BindaasUser.LAST_NAME, request.getLastName());
				return bindaasUser;
			}

		} catch (Exception e) {
			log.error(e);
			throw new JWTManagerException(e, Reason.PROCESSING_ERROR);
		}

		return null;
	}

	@Override
	public List<UserRequest> listJWT() throws JWTManagerException {
		Session session = sessionFactory.openSession();

		try {
			@SuppressWarnings("unchecked")
			List<UserRequest> listOfValidTokens = (List<UserRequest>) session
					.createCriteria(UserRequest.class)
					.add(Restrictions.eq("stage", Stage.accepted.name()))
					.add(Restrictions.gt("dateExpires", new Date()))
					.add(Restrictions.isNotNull("jwt"))
					.list();
			return listOfValidTokens;
		} catch (Exception e) {
			log.error(e);
			throw new JWTManagerException(e , Reason.PROCESSING_ERROR);
		} finally {
			session.close();
		}
	}

	@Override
	public Integer revokeJWT(BindaasUser bindaasUser, String initiatedBy ,String comments , ActivityType activityType)
			throws JWTManagerException {
		Session session = sessionFactory.openSession();
		try{
			String emailAddress = bindaasUser
					.getProperty(BindaasUser.EMAIL_ADDRESS) != null ? bindaasUser
					.getProperty(BindaasUser.EMAIL_ADDRESS).toString()
					: bindaasUser.getName() + "@" + bindaasUser.getDomain();

			@SuppressWarnings("unchecked")
			List<UserRequest> listOfValidTokens = (List<UserRequest>) session.createCriteria(UserRequest.class).
					add(Restrictions.eq("stage", Stage.accepted.name())).
					add(Restrictions.eq("emailAddress", emailAddress)).
					add(Restrictions.isNotNull("jwt")).
					list();

			if(listOfValidTokens!=null && listOfValidTokens.size() > 0)
			{
				session.beginTransaction();
				HistoryLog historyLog = new HistoryLog();
				for(UserRequest usrRequest : listOfValidTokens)
				{
					usrRequest.setStage(Stage.revoked);
					invalidateJWT(usrRequest.getJWT());

					historyLog.setActivityType(activityType.toString());
					historyLog.setComments(comments);
					historyLog.setInitiatedBy(initiatedBy);
					historyLog.setUserRequest(usrRequest);
					session.save(historyLog);
				}
				session.getTransaction().commit();

				return listOfValidTokens.size();
			}
			else
				return 0;


		}catch(Exception e)
		{
			log.error(e);
			session.getTransaction().rollback();
			throw new JWTManagerException(e ,Reason.PROCESSING_ERROR);
		}
		finally{
			if(session!=null)
				session.close();
		}

	}

	public Date getExpires(String token) {
		DecodedJWT jwt = JWT.decode(token);
		return jwt.getExpiresAt();
	}

	public String getEmailAddress(Long id){
		Session session = sessionFactory.openSession();

		@SuppressWarnings("unchecked")
		List<UserRequest> userRequest = (List<UserRequest>) session.createCriteria(UserRequest.class).
				add(Restrictions.eq("id", id)).list();

		return userRequest.get(0).getEmailAddress();
	}


	public void init() throws Exception {

        //FIXME: Write to file required?
//		SecureRandom sr = new SecureRandom();
//		byte[] secretArray = new byte[64];
//		sr.nextBytes(secretArray);
//		secret = new String(secretArray);
//		secret="fsdfdskfg3et4723truwewebd";
//		try (PrintWriter out = new PrintWriter("secret.txt")) {
//			out.println("secret: "+secret);
//			out.println("secretArray: "+Arrays.toString(secretArray));
//		}
//		catch (Exception e){
//			log.error(e);
//		}
	}

}
