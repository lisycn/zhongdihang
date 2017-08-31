package com.zhongdihang.bankdispatch.modular.service.impl;

import com.zhongdihang.bankdispatch.modular.dao.AssessDao;
import com.zhongdihang.bankdispatch.modular.dao.DispatchDao;
import com.zhongdihang.bankdispatch.modular.dao.FileDao;
import com.zhongdihang.bankdispatch.modular.dao.PreReportDao;
import com.zhongdihang.bankdispatch.modular.domain.Dispatch;
import com.zhongdihang.bankdispatch.modular.domain.PreReport;
import com.zhongdihang.bankdispatch.modular.service.DispatchTrackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by win10 on 2017/08/17.
 */
@Service
public class DispatchTrackServiceImpl implements DispatchTrackService
{
    @Autowired
    private AssessDao assessDao;
    @Autowired
    private DispatchDao dispatchDao;
    @Autowired
    private PreReportDao preReportDao;
    @Autowired
    private FileDao fileDao;

    @Override
    public Dispatch dispatchUpdate(int Status,Long dispatchId,Long fileId)
    {
        Dispatch dispatch = dispatchDao.findOne(dispatchId);
        PreReport preReport=new PreReport();
        preReport.setFile(fileDao.findOne(fileId));
        preReport.setDispatch(dispatch);
        //==========================================
        //1.进行中2.已完成，3.有问题
        //==========================================
        switch (Status)
        {
            case 2:
                dispatch.setStatus("2");
                break;
            case 3:
                dispatch.setStatus("3");
                break;
        }
        preReportDao.save(preReport);
        return dispatchDao.save(dispatch);
    }
}
