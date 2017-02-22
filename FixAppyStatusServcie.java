package me.ele.bpm.runshop.impl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import me.ele.arch.etrace.agent.aop.Traceable;
import me.ele.bpm.bus.policy.core.service.IPolicyService;
import me.ele.bpm.runshop.core.constants.RunshopServiceConstants;
import me.ele.bpm.runshop.core.form.RunshopOperateRecordForm;
import me.ele.bpm.runshop.core.model.RunshopApplicationInfo;
import me.ele.bpm.runshop.core.model.RunshopOperateRecord;
import me.ele.bpm.runshop.core.model.constant.RunShopStatus;
import me.ele.bpm.runshop.core.model.constant.RunshopOperateType;
import me.ele.bpm.runshop.core.service.IFixAppyStatusServcie;
import me.ele.bpm.runshop.impl.client.CoffeeHrClientService;
import me.ele.bpm.runshop.impl.dao.runshop.RunshopMapper;
import me.ele.bpm.runshop.impl.dao.runshop.RunshopOperateRecordMapper;
import me.ele.bpm.runshop.impl.service.async.RunshopAsyncService;
import me.ele.bpm.runshop.impl.thirdparty.HermesClient;
import me.ele.bpm.runshop.impl.threads.DistributeThread;
import me.ele.bpm.runshop.impl.utils.*;
import me.ele.coffee.model.hr.model.User;
import me.ele.contract.exception.ServerException;
import me.ele.contract.exception.ServiceException;
import me.ele.elog.Log;
import me.ele.elog.LogFactory;
import me.ele.eve.soa.core.dto.message.EveMessageForm;
import me.ele.eve.soa.core.service.IMessageService;
import me.ele.napos.operation.service.lucifer.api.LMerchantService;
import me.ele.napos.operation.shared.payload.entities.applybase.OperatorType;
import me.ele.napos.operation.shared.payload.entities.merchant.CreateRestaurantResult;
import me.ele.pcd.dom.service.service.IOpenstoreService;
import me.ele.work.flow.engine.core.dto.RunshopAuditSimpleStatusDto;
import me.ele.work.flow.engine.core.service.IRunshopWorkFlowService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Created by lanpishu on 16/11/8.
 */
@Service
public class FixAppyStatusServcie implements IFixAppyStatusServcie{


    /**
     * elog.
     */
    private static Log logger = LogFactory.getLog(FixAppyStatusServcie.class);

    /**
     * runshopMapper.
     */
    @Autowired
    private RunshopMapper runshopMapper;

    @Autowired
    private RunshopService runshopService;


    @Autowired
    private RunshopAsyncService runshopAsyncService;

    /**
     * hermesClient.
     */
    @Autowired
    private HermesClient hermesClient;

    /**
     * runshopOperateRecordMapper.
     */
    @Autowired
    private RunshopOperateRecordMapper runshopOperateRecordMapper;

    /**
     * coffeeHrClientService.
     */
    @Autowired
    private CoffeeHrClientService coffeeHrClientService;

    /**
     * policyService.
     */
    @Autowired
    private IPolicyService policyService;

    /**
     * messageService.
     */
    @Autowired
    private IMessageService messageService;

    @Autowired
    private IOpenstoreService iOpenstoreService;

    @Autowired
    private IRunshopWorkFlowService runshopWorkFlowService;

    @Autowired
    private LMerchantService lMerchantService;

    @Override
    public void fixAppyStatus()
            throws ServiceException {

        logger.info("enter_FixAppyStatus#fixAppyStatus");
		/* 审核状态 */
        int i = 0;

        while (true) {

            List<RunshopApplicationInfo> runshopApplicationInfoList = runshopMapper.searchRunshopInfoForXY(
                    null, null, 0, null, null,
                    null, null, 20000, 20000 * i);

            if(CollectionUtils.isEmpty(runshopApplicationInfoList)){
                logger.info("finish_FixAppyStatus#fixAppyStatus runshopIdList is empty,round {}" , i);
                break;
            }

            List<Integer> runshopIdList = runshopApplicationInfoList.stream().map(RunshopApplicationInfo::getId)
                    .collect(Collectors.toList());

            logger.info("FixAppyStatus#fixAppyStatus runshopIdList :{} round {}", runshopIdList,i);
            //1 查询出workflow中处于已通过状态的rstid
            List<Integer> filteredList = filterRstID(runshopIdList);

            reAuditApply(filteredList,RunShopStatus.PASS);
            logger.info("finish_FixAppyStatus#fixAppyStatus runshopIdList :{},round {}" ,runshopIdList, i);

            i++;
        }
        logger.info("exit_FixAppyStatus#fixAppyStatus");
    }


    @Override
    public List<Integer> loadListFixAppyStatus()
            throws ServiceException {

        logger.info("enter_FixAppyStatus#loadListFixAppyStatus");
		/* 审核状态 */
        int i = 0;

        List<Integer> result = Lists.newLinkedList();

        while (true) {

            List<RunshopApplicationInfo> runshopApplicationInfoList = runshopMapper.searchRunshopInfoForXY(
                    null, null, 0, null, null,
                    null, null, 20000, 20000 * i);

            if(CollectionUtils.isEmpty(runshopApplicationInfoList)){
                logger.info("finish_FixAppyStatus#loadListFixAppyStatus runshopIdList is empty,round {}" , i);
                break;
            }

            List<Integer> runshopIdList = runshopApplicationInfoList.stream().map(RunshopApplicationInfo::getId)
                    .collect(Collectors.toList());

            logger.info("FixAppyStatus#loadListFixAppyStatus runshopIdList :{} round {}", runshopIdList,i);
            //1 查询出workflow中处于已通过状态的rstid
            List<Integer> filteredList = filterRstID(runshopIdList);
            result.addAll(filteredList);
           // reAuditApply(filteredList,RunShopStatus.PASS);
            logger.info("finish_FixAppyStatus#loadListFixAppyStatus runshopIdList :{},round {}" ,runshopIdList, i);

            i++;
        }
        logger.info("exit_FixAppyStatus#loadListFixAppyStatus, {}",result);
        return result     ;
    }



    @Override
    public void fixAppyStatusForNeedModify()
            throws ServiceException {

        logger.info("enter_FixAppyStatus#fixAppyStatusForNeedModify");
		/* 审核状态 */
        int i = 0;

        while (true) {

            List<RunshopApplicationInfo> runshopApplicationInfoList = runshopMapper.searchRunshopInfoForXY(
                    null, null, 0, null, null,
                    null, null, 20000, 20000 * i);

            if(CollectionUtils.isEmpty(runshopApplicationInfoList)){
                logger.info("finish_FixAppyStatus#fixAppyStatusForNeedModify runshopIdList is empty,round {}" , i);
                break;
            }

            List<Integer> runshopIdList = runshopApplicationInfoList.stream().map(RunshopApplicationInfo::getId)
                    .collect(Collectors.toList());

            logger.info("FixAppyStatus#fixAppyStatusForNeedModify runshopIdList :{} round {}", runshopIdList,i);
            //1 查询出workflow中处于已通过状态的rstid
            List<Integer> filteredList = filterRstIDForNeedFix(runshopIdList);

            reAuditApply(filteredList,RunShopStatus.NEED_FIX);
            logger.info("finish_FixAppyStatus#fixAppyStatusForNeedModify runshopIdList :{},round {}" ,runshopIdList, i);

            i++;
        }
        logger.info("exit_FixAppyStatus#fixAppyStatusForNeedModify");
    }

    private List<Integer> filterRstID(List<Integer> originList){

        logger.info("FixAppyStatus#filterRstID");


        List<FutureTask<Map<Integer, RunshopAuditSimpleStatusDto>>> taskList = new ArrayList<>();

        List<List<Integer>> sublists = Lists.partition(originList, 1000);
        for(List<Integer> sublist : sublists ) {
            Task task = new Task(sublist);
            FutureTask<Map<Integer, RunshopAuditSimpleStatusDto>> futureTask = new FutureTask<>(task);
            taskList.add(futureTask);
            ThreadPoolHolder.getInstance().submit(futureTask);
            //  asyncTaskExecutor.submit(futureTask1);
        }

        List<Integer> result = Lists.newLinkedList();

        for (FutureTask<Map<Integer, RunshopAuditSimpleStatusDto>> ft : taskList) {
            try {
                //FutureTask的get方法会自动阻塞,直到获取计算结果为止
                result.addAll(ft.get(20, TimeUnit.SECONDS).keySet());
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.error("");
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                logger.error("");
                e.printStackTrace();
            }
        }

        logger.info("FixAppyStatus#filterRstID, result {}", result);
        return result;
    }

    private List<Integer> filterRstIDForNeedFix(List<Integer> originList){

        logger.info("FixAppyStatus#filterRstIDForNeedFix");


        List<FutureTask<Map<Integer, RunshopAuditSimpleStatusDto>>> taskList = new ArrayList<>();

        List<List<Integer>> sublists = Lists.partition(originList, 1000);
        for(List<Integer> sublist : sublists ) {
            NeedModifyTask task = new NeedModifyTask(sublist);
            FutureTask<Map<Integer, RunshopAuditSimpleStatusDto>> futureTask = new FutureTask<>(task);
            taskList.add(futureTask);
            ThreadPoolHolder.getInstance().submit(futureTask);
            //  asyncTaskExecutor.submit(futureTask1);
        }

        List<Integer> result = Lists.newLinkedList();

        for (FutureTask<Map<Integer, RunshopAuditSimpleStatusDto>> ft : taskList) {
            try {
                //FutureTask的get方法会自动阻塞,直到获取计算结果为止
                result.addAll(ft.get(20, TimeUnit.SECONDS).keySet());
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.error("");
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                logger.error("");
                e.printStackTrace();
            }
        }

        logger.info("FixAppyStatus#filterRstIDForNeedFix, result {}", result);
        return result;
    }


    class Task implements Callable<Map<Integer, RunshopAuditSimpleStatusDto>> {


        private List<Integer> runshopIdList;
        public Task(List<Integer> list){
            this.runshopIdList = list;
        }

        @Override
        public Map<Integer, RunshopAuditSimpleStatusDto> call() throws Exception {
//            List<TalosRestaurantInfo> result = new ArrayList<TalosRestaurantInfo>();
            logger.info("FixAppyStatus#futureTask, runshopIdList {}", runshopIdList);

            Map<Integer, RunshopAuditSimpleStatusDto> runshopAuditSimpleStatusDtos = runshopWorkFlowService
                    .searchAuditRunshopStatus(runshopIdList, me.ele.work.flow.engine.core.constants.AuditStatusEnum.PASS);

            logger.info("FixAppyStatus#futureTask, result {}", runshopAuditSimpleStatusDtos);
            return runshopAuditSimpleStatusDtos;
        }
    }

    class NeedModifyTask implements Callable<Map<Integer, RunshopAuditSimpleStatusDto>> {


        private List<Integer> runshopIdList;
        public NeedModifyTask(List<Integer> list){
            this.runshopIdList = list;
        }

        @Override
        public Map<Integer, RunshopAuditSimpleStatusDto> call() throws Exception {
//            List<TalosRestaurantInfo> result = new ArrayList<TalosRestaurantInfo>();
            logger.info("FixAppyStatus#needModifyTask, runshopIdList {}", runshopIdList);

            Map<Integer, RunshopAuditSimpleStatusDto> runshopAuditSimpleStatusDtos = runshopWorkFlowService
                    .searchAuditRunshopStatus(runshopIdList, me.ele.work.flow.engine.core.constants.AuditStatusEnum.NEED_MODIFY);

            logger.info("FixAppyStatus#needModifyTask, result {}", runshopAuditSimpleStatusDtos);
            return runshopAuditSimpleStatusDtos;
        }
    }


    private void reAuditApply(List<Integer> reauditList, RunShopStatus status) {

            logger.info("FixAppyStatus.reAuditRst start with: {}", reauditList);

         //   RunShopStatus status = RunShopStatus.PASS;

            Map<me.ele.work.flow.engine.core.constants.AuditProcessEnum, String> reason =null;

            //随机取了一个上次审核通过的人员52294，这位同学的kpi这个月应该会很高，哈哈哈

            int userId = 52294;
            for (Integer id : reauditList) {
                try {
                    setAuditStatus(id, status, userId, reason);
                    logger.info("reAuditApply_succeed,applyId {}", id);

                }catch(Exception e){
                    logger.error("reAuditApply_failed,applyId {},e{}", id,e);
                }
        }
    }
    @Traceable
    @Deprecated
    @Override
    public void setAuditStatus(int id, RunShopStatus status, int userId,
                               Map<me.ele.work.flow.engine.core.constants.AuditProcessEnum, String> reason) throws ServiceException,
            ServerException {
        logger.info("FixAppyStatusServcie.setAuditStatus start with:{}, {}, {}, {}", id, status, userId, reason);

        RunshopApplicationInfo runshopApplicationInfo = validParameter(id, status, userId);

        User user = coffeeHrClientService.getUserById(userId);

		/* 更新开店申请状态信息 */
        String auditReason = generateReason(reason);
        try {
            logger.info("FixAppyStatusServcie.setAuditStatus set runshop app with status:{}", status.getValue());
            runshopMapper.updateStatus(id, auditReason, status.getValue());
        } catch (Exception e) {
            logger.error("FixAppyStatusServcie#setAuditStatus[updateRunshopApplicationInfoStatusById] error", e);
            throw ExceptionUtil.createServerException(ExceptionCode.UPDATE_RUNSHOP_STATUS_ERROR);
        }
        String operateData = "";

        if (status == RunShopStatus.PASS) {

            try {
                runshopAsyncService.asyncAfterProcess(runshopApplicationInfo, userId, user, id,runshopService);
            }catch (Exception e){
                logger.error("error for async process after set status {} ",runshopApplicationInfo);
            }

         //   assignBD(runshopApplicationInfo, rstId, id);
        } else if (status == RunShopStatus.NEED_FIX) {
            runshopAsyncService.asyncSendMessage(runshopApplicationInfo,auditReason,user,id,userId,operateData);

        }
        logger.info("finish_setAuditStatus,applyId {}", id);

    }

    private void assignBD(RunshopApplicationInfo runshopApplicationInfo, int rstId, int id) {

        if (runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD
                && runshopApplicationInfo.getHead_id() == 0) {

            logger.info("FixAppyStatusServcie.setAuditStatus() BD报备的开店申请，调用分配接口。");

            ThreadPoolHolder.getInstance().execute(
                    new DistributeThread(rstId, runshopApplicationInfo.getUser_id(), 1, runshopService, runshopApplicationInfo));
        } else if (runshopApplicationInfo.getHead_id() != 0
                && runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD) {
            ThreadPoolHolder.getInstance().execute(
                    new DistributeThread(rstId, runshopApplicationInfo.getUser_id(), 2, runshopService, runshopApplicationInfo));
        } else {
            ThreadPoolHolder.getInstance().execute(
                    new DistributeThread(rstId, runshopApplicationInfo.getUser_id(), 0, runshopService, runshopApplicationInfo));
        }

        try {
            // 如果是报备添加eve消息推送给市场经理
            if (runshopApplicationInfo.getSource() == RunshopServiceConstants.SOURCE_BD) {

                EveMessageForm messageForm = new EveMessageForm();
                messageForm.setTitle("您报备的门店已通过审核！");
                String content = String.format(ProjectConstant.EVE_RUNSHOP_QUALIFIED_MESSAGE_TMP,
                        runshopApplicationInfo.getStore_name(), runshopApplicationInfo.getAddress(),
                        runshopApplicationInfo.getMobile());
                messageForm.setUrl(ProjectConstant.EVE_RUNSHOP_URL_CODE); // 开店管理详情URL
                Map<String, Object> param = new HashMap<>();
                param.put("id", id);
                messageForm.setUrlParam(param);
                messageForm.setContent(content);
                List<Integer> userIdList = new ArrayList<>(1);
                userIdList.add(runshopApplicationInfo.getUser_id());
                messageForm.setUserIdList(userIdList);
                messageForm.setBuss_type(ProjectConstant.RUNSHOP_EVE_MESSAGE_BUSS_TYPE);
                messageService.pushMessage(messageForm);
            }
        } catch (Exception e1) {
            logger.error("FixAppyStatusServcie#setAuditStatus error", e1);
        }
    }

    // 设置开店申请分配状态为分配失败
    private void setDistributeFalse(int runshopId) {
        RunshopOperateRecordForm runshopOperateRecordForm = new RunshopOperateRecordForm();
        runshopOperateRecordForm.setCoffeeUserName("系统");
        runshopOperateRecordForm.setCoffeeUserId(0);
        runshopOperateRecordForm.setCoffeeUserEmail("");
        runshopOperateRecordForm.setOperateData("分配市场经理失败");
        runshopOperateRecordForm.setRunshopId(runshopId);
        runshopOperateRecordForm.setOperateType(4);
        try {
            runshopService.createRunshopOperateRecord(runshopOperateRecordForm);
        } catch (ServiceException | ServerException e) {
            logger.error("FixAppyStatusServcie#setDistributeFalse#createRunshopOperateRecord error, runshopId : " + runshopId
                    + ", e: ", e);
        }
    }

    /**
     * 设置门店的分配状态
     *
     * @param runshopId
     * @param is_distribution
     */
    private void setNotDistribution(int runshopId, int is_distribution) {
        runshopMapper.setIsDistribution(runshopId, is_distribution);
    }

    private RunshopApplicationInfo validParameter(int id, RunShopStatus status, int userId) throws ServiceException,
            ServerException {

        if (0 == id) {
            throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_ID_NULL);
        }
        // if (CollectionUtils.isEmpty(reason)) {
        // throw。
        // ExceptionUtil.createServiceException(ExceptionCode.CHECK_FAIL_RUNSHOP_REASON_IS_NULL);
        // }

		/* 获取开店申请信息 */
        RunshopApplicationInfo runshopApplicationInfo;
        try {
            runshopApplicationInfo = runshopMapper.getRunshopApplicationInfoById(id);
//            if (runshopApplicationInfo.getRst_id() != 0) {
//                throw ExceptionUtil.createServerException(ExceptionCode.RUNSHOP_ALREADY_AUDITED);
//            }
        } catch (Exception e) {
            logger.error("FixAppyStatusServcie#setAuditStatus[getRunshopApplicationInfoById] error", e);
            throw ExceptionUtil.createServerException(ExceptionCode.GET_RUNSHOP_BY_ID_ERROR);
        }
        if (null == runshopApplicationInfo || 0 == runshopApplicationInfo.getId()) {
            logger.warn("FixAppyStatusServcie#setAuditStatus[getRunshopApplicationInfoById] runshop id null");
            throw ExceptionUtil.createServiceException(ExceptionCode.GET_RUNSHOP_BY_ID_NULL);
        }
        if (0 != runshopApplicationInfo.getStatus()) {
            logger.info("FixAppyStatusServcie#setAuditStatus runshop status not allow, status is:{}",
                    runshopApplicationInfo.getStatus());
            throw ExceptionUtil.createServiceException(ExceptionCode.UNQUALIFIED_RUNSHOP_STATUS_ERROR);
        }

        return runshopApplicationInfo;
    }

    private String generateReason(Map<me.ele.work.flow.engine.core.constants.AuditProcessEnum, String> reason) {
        if(CollectionUtils.isEmpty(reason)){
            return "";
        }else{
            Map<Integer, String> map = new HashMap<>();

            reason.entrySet().forEach(e -> map.put(e.getKey().getType(), e.getValue()));
            try {
                return JsonHelper.toJsonString(map);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return "审核原因转换失败";
        }
    }


    private int insertRunshopOperateRecord(int runshopId, int coffeeUserId, String coffeeUserEmail,
                                           String coffeeUserName, String operateData, int operateType) throws ServiceException, ServerException {
        RunshopOperateRecord runshopOperateRecord = new RunshopOperateRecord();
        runshopOperateRecord.setRunshop_id(runshopId);
        runshopOperateRecord.setCoffee_user_id(coffeeUserId);
        runshopOperateRecord.setCoffee_user_email(coffeeUserEmail);
        runshopOperateRecord.setCoffee_user_name(coffeeUserName);
        runshopOperateRecord.setOperate_data(operateData);
        runshopOperateRecord.setOperate_type(operateType);
        int runshopOperateRecordId;
        try {
            runshopOperateRecordId = runshopOperateRecordMapper.insertRunshopOperateRecord(runshopOperateRecord);
        } catch (Exception e) {
            logger.error("FixAppyStatusServcie#insertRunshopOperateRecord error", e);
            throw ExceptionUtil.createServerException(ExceptionCode.INSERT_RUNSHOP_OPERATE_RECORD_ERROR);
        }
        return runshopOperateRecordId;
    }


    /**
     * 设置门店的分配状态
     *
     * @param rstId
     * @param is_distribution
     */
    private void setIsDistribution(int rstId, int is_distribution) throws ServerException, ServiceException {
        logger.info("FixAppyStatusServcie#setIsDistribution,rstId:{},is_distribution:{}", rstId, is_distribution);
        runshopMapper.setIsDistribution(rstId, is_distribution);
    }
    /**
     * 根据传入的餐厅ID和用户ID，分配BD报备的新店。 本接口仅限于BD报备调用
     *
     * @param rstID
     * @param userID
     * @throws ServiceException
     * @throws ServerException
     */
    private void distributeApplicationFromBD(int rstID, int userID, RunshopApplicationInfo info)
            throws ServiceException, ServerException {

        logger.info("FixAppyStatusServcie.distributeApplicationFromBD() start with rstID:{}, userID:{}", rstID, userID);
        try {

            if (rstID < 0 || userID < 0) {
                logger.info("FixAppyStatusServcie.distributeApplicationFromBD() get invalid input, return.");
                return;

            }

            setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED);

            for (int i = 0; i < RunshopServiceConstants.RETRY_TIMES; i++) {
                try {

                    policyService.distributeRstCreatedByBD(rstID, userID, info, new User());
                    break;

                } catch (Exception e) {
                    logger.error("FixAppyStatusServcie.distributeApplicationFromBD() failed at:{}", e);
                    logger.info("FixAppyStatusServcie.distributeApplicationFromBD() retry!!");

                }

            }

            logger.info("FixAppyStatusServcie.distributeApplicationFromBD() distribute failed after {} times, "
                    + "set to not distributed and wait for script handling.", RunshopServiceConstants.RETRY_TIMES);
            setIsDistribution(rstID, RunshopServiceConstants.NOT_DISTRIBUTED);

        } catch (Exception e) {
            logger.error("FixAppyStatusServcie.distributeApplicationFromBD()failed at:{}", e);
            throw ExceptionUtil.createServerException(ExceptionCode.DISTRIBUTE_STORE_FAILED);

        }

    }

    /**
     * 根据传入的餐厅ID和用户ID，分配BD报备的新店。 本接口仅限于BD报备调用
     *
     * @param rstID
     * @param userID
     * @throws ServiceException
     * @throws ServerException
     */
    private void distributeApplicationForSigGka(int rstID, int userID, RunshopApplicationInfo info)
            throws ServiceException, ServerException {

        logger.info("FixAppyStatusServcie.distributeApplicationFromBD() start with rstID:{}, userID:{}, {}", rstID, userID,
                info);
        try {

            if (rstID < 0 || userID < 0) {
                logger.info("FixAppyStatusServcie.distributeApplicationForSigGka() get invalid input, return.");
                return;

            }

            setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED);

            for (int i = 0; i < RunshopServiceConstants.RETRY_TIMES; i++) {
                try {

                    policyService.distributeSigGkaShop(rstID, info, new User());
                    break;

                } catch (Exception e) {
                    logger.error("FixAppyStatusServcie.distributeApplicationForSigGka() failed at:{}", e);
                    logger.info("FixAppyStatusServcie.distributeApplicationForSigGka() retry!!");

                }

            }

            logger.info("FixAppyStatusServcie.distributeApplicationFromBD() distribute failed after {} times, "
                    + "set to not distributed and wait for script handling.", RunshopServiceConstants.RETRY_TIMES);
            setIsDistribution(rstID, RunshopServiceConstants.NOT_DISTRIBUTED);

        } catch (Exception e) {
            logger.error("FixAppyStatusServcie.distributeApplicationFromBD()failed at:{}", e);
            throw ExceptionUtil.createServerException(ExceptionCode.DISTRIBUTE_STORE_FAILED);

        }

    }

    /**
     * 根据传入的餐厅ID和用户ID，分配新店。
     *
     * @param rstID
     * @throws ServiceException
     * @throws ServerException
     */
    private void distributeApplication(int rstID) throws ServiceException, ServerException {

        logger.info("FixAppyStatusServcie.distributeApplication() start with rstID:{}", rstID);
        try {

            if (rstID < 0) {
                logger.info("FixAppyStatusServcie.distributeApplication() get invalid input, return.");
                return;
            }

            setIsDistribution(rstID, RunshopServiceConstants.DISTRIBUTED);

            for (int i = 0; i < RunshopServiceConstants.RETRY_TIMES; i++) {
                try {
                    policyService.distributeByRstID(rstID);
                    break;

                } catch (Exception e) {
                    logger.error("FixAppyStatusServcie.distributeApplication() failed at:{}", e);
                    logger.info("FixAppyStatusServcie.distributeApplication() retry!!");
                }
            }

            logger.info("FixAppyStatusServcie.distributeApplication() distribute failed after {} times, "
                    + "set to not distributed and wait for script handling.", RunshopServiceConstants.RETRY_TIMES);
            setIsDistribution(rstID, RunshopServiceConstants.NOT_DISTRIBUTED);

        } catch (Exception e) {
            logger.error("FixAppyStatusServcie.distributeApplication()failed at:{}", e);
            throw ExceptionUtil.createServerException(ExceptionCode.DISTRIBUTE_STORE_FAILED);
        }

    }

}
