package me.ele.bpm.runshop.core.service;

import me.ele.bpm.runshop.core.model.constant.RunShopStatus;
import me.ele.contract.exception.ServerException;
import me.ele.contract.exception.ServiceException;
import me.ele.work.flow.engine.core.constants.AuditProcessEnum;

import java.util.List;
import java.util.Map;

/**
 * Created by lanpishu on 16/11/10.
 */
public interface IFixAppyStatusServcie {
    void fixAppyStatus()
            throws ServiceException;

    List<Integer> loadListFixAppyStatus()
            throws ServiceException;



    void fixAppyStatusForNeedModify()
            throws ServiceException;

    @Traceable
    @Deprecated
    void setAuditStatus(int id, RunShopStatus status, int userId,
                        Map<AuditProcessEnum, String> reason) throws ServiceException,
            ServerException;
}
