package com.zhongdihang.bankdispatch.modular.service.impl;

import com.alibaba.fastjson.JSON;
import com.zhongdihang.bankdispatch.common.constant.DictEnum;
import com.zhongdihang.bankdispatch.common.constant.SMSTemplateEnum;
import com.zhongdihang.bankdispatch.common.sms.DispatchSMSTemplate;
import com.zhongdihang.bankdispatch.common.sms.SendMsgUtil;
import com.zhongdihang.bankdispatch.common.util.RandomUtil;
import com.zhongdihang.bankdispatch.core.controoller.BaseController;
import com.zhongdihang.bankdispatch.modular.dao.*;
import com.zhongdihang.bankdispatch.modular.domain.*;
import com.zhongdihang.bankdispatch.modular.service.DictService;
import com.zhongdihang.bankdispatch.modular.service.DispatchService;
import com.zhongdihang.bankdispatch.modular.service.dto.AssessDto;
import com.zhongdihang.bankdispatch.modular.service.dto.DispatchDto;
import com.zhongdihang.bankdispatch.modular.service.dto.GuarantyDto;
import com.zhongdihang.bankdispatch.modular.service.form.newGUarantyForm;
import com.zhongdihang.framework.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by win10 on 2017/08/16.
 */
@Service
public class DispatchServiceImpl  implements DispatchService {

    @Autowired
    private AssessDao assessDao;
    @Autowired
    private DispatchTrackDao dispatchTrackDao;
    @Autowired
    private DispatchDao dispatchDao;
    @Autowired
    private FileDao fileDao;
    @Autowired
    private GuarantyDao guarantyDao;
    @Autowired
    private BaseController baseController;

    @Override
    public AssessDto randomDispatch(Long bankId,User user,List<newGUarantyForm> gUarantyFormList)
    {
        //======================================
        // 生成订单
        //======================================
        DispatchTrack dispatchTrack = new DispatchTrack();
        Dispatch dispatch = new Dispatch();
        if (user!=null)
        {
            dispatch.setBankUser(user);
            dispatch.setBank(user.getBank());
            //======================================
            //1.进行中2.已完成，3.有问题
            //======================================
            dispatch.setStatus("1");
            dispatch.setType("1");
            dispatch.setCreateTime(new Date());
            dispatch.setCreateUser(user);
            dispatch.setDispatchNo("112233");
            dispatchTrack.setCreateUser(user);
            dispatchTrack.setCreateTime(new Date());
            dispatchTrack.setNodeNo(1212);
            dispatchTrack.setRemark("remark");
            dispatchTrack.setContact(user.getUserName());
            dispatchTrack.setContactTelephone(user.getTelephone());
        }

        //=============================================================
        // 派单流程
        //=============================================================
        int weight=0;
        AssessDto assessDto = new AssessDto();
        List<AssessDto> assessDtoList = new ArrayList<>();
        int guarantyType = Integer.valueOf(gUarantyFormList.get(0).getGuarantyType());
        List<Assess> assessList = assessDao.findAssessByBankId(bankId,guarantyType);
        //================================================
        //计算总权重
        //================================================
        for (Assess assess:assessList)
        {
            weight+=assess.getWeight();
        }
        //================================================
        //获取随机数
        //================================================
        int s = RandomUtil.getRandom(0,weight);
        //================================================
        //判断是否有评级机构
        //================================================
        if (assessList.size()==0)
        {
            return null;
        }
        else if (assessList.size()==1)
        {
            //================================================
            //设置选中的评估机构id及名称
            //================================================
            assessDto.setId(assessList.get(0).getId().toString());
            assessDto.setName(assessList.get(0).getName());
            assessDtoList.add(assessDto);
        }
        else
        {
            int d1=0;
            int d2=0;
            for (Assess assess:assessList)
            {
                d2 = d1 + assess.getWeight();
                if (d1 <= s && s <= d2)
                {
                    assessDto.setId(assess.getId().toString());
                    assessDto.setName(assess.getName());
                    assessDtoList.add(assessDto);
                    break;
                }
                d1 += assess.getWeight();
            }
            //================================================
            //判断是否是唯一的评级机构，若不是则重新随机
            //================================================
            if (assessDtoList.size()>=2)
            {
                assessDto =  assessDtoList.get(RandomUtil.getRandom(0,assessDtoList.size()));
            }
            else
            {
                assessDto = assessDtoList.get(0);
            }
        }

        Assess assess_ = assessDao.findOne(Long.valueOf(assessDto.getId()));
        if (assess_!=null){
            dispatch .setAssess(assess_);
            dispatch.setAssessUser(user);
            dispatchTrack.setAssessname(assess_.getName());
            dispatchTrack.setAssessTelephone(assess_.getTelephone());
        }

        dispatchDao.save(dispatch);
        //===================================================
        //生成单据号
        //===================================================
        if (dispatch!=null)
        {
            SimpleDateFormat sf=new SimpleDateFormat("yyyyMMddHHmmss");
            String date=sf.format(new Date());
            String dispatchId = dispatch.getId().toString();
            dispatchId.substring(dispatchId.length()-4,dispatchId.length());
            String dispatchNo = dispatch.getType()+date+dispatchId;
            dispatch.setDispatchNo(dispatchNo);
            dispatchTrack.setDispatchNo(dispatchNo);
        }

        //====================================================
        //抵押物
        //====================================================
        if (gUarantyFormList!=null)
        {
            for (newGUarantyForm gUarantyForm:gUarantyFormList)
            {
                Guaranty guaranty =  new Guaranty();
                guaranty.setFile(fileDao.findOne(gUarantyForm.getFileId()));
                guaranty.setClientName(gUarantyForm.getClientName());
                guaranty.setClientTelephone(gUarantyForm.getClientTelephone());
                guaranty.setHandlerName(gUarantyForm.getHandlerName());
                guaranty.setHandlerTelephone(gUarantyForm.getHandler_telephone());
                guaranty.setGuarantyType(gUarantyForm.getGuarantyType());
                guaranty.setDispatch(dispatch);
                guaranty.setCreateTime(new Date());
                guaranty.setDocument("12");
                guarantyDao.save(guaranty);
            }
        }

        //====================================================
        // 发送短信
        //====================================================
        String bankName = user.getBank().getName();
        String contact = user.getUserName();//客户经理
        String contactPhone = user.getTelephone();

        DispatchSMSTemplate dispatchSMSTemplate=new DispatchSMSTemplate(bankName,contact,contactPhone);
        SendMsgUtil.sendMsg(assess_.getTelephone(), JSON.toJSONString(dispatchSMSTemplate),
                SMSTemplateEnum.DISPATCH.getTemplate());

        dispatchDao.save(dispatch);
        dispatchTrackDao.save(dispatchTrack);
        return assessDto;
    }

    @Override
    public Page<Dispatch> findDispatch(String status_, Bank bank_, Assess assess_, String dispatchNo_, PageRequest page)
    {
        Pageable pageable = page;
        Page<Dispatch> Dispatchs= dispatchDao.findAll(new Specification<Dispatch>()
        {
            @Override
            public Predicate toPredicate(Root<Dispatch> root, CriteriaQuery<?> query, CriteriaBuilder cb)
            {
                Path<String> status = root.get("status");
                Path<Bank> bank = root.get("bank");
                Path<Assess> assess = root.get("assess");
                Path<String> dispatchNo = root.get("dispatchNo");
                List<Predicate> list = new ArrayList<Predicate>();
                if (!org.springframework.util.StringUtils.isEmpty(status_))
                {
                    list.add(cb.and(cb.and(cb.equal(status,status_),
                            cb.and(cb.equal(bank,bank_)))));
                }
                else if (!org.springframework.util.StringUtils.isEmpty(dispatchNo_))
                {
                    list.add(cb.and(cb.and(cb.equal(bank,bank_)),
                            cb.and(cb.equal(dispatchNo,dispatchNo_))));
                }
                if (bank_!=null)
                {
                    list.add(cb.and(cb.and(cb.equal(bank,bank_))));
                }
                else if (assess_!=null)
                {
                    list.add(cb.and(cb.and(cb.equal(assess,assess_))));
                }
                Predicate[] p = new Predicate[list.size()];
                return cb.and(list.toArray(p));
            }
        },pageable);
        return Dispatchs;
    }

    @Override
    public Dispatch dispatchByFormal(Long dispatchId) {
        Dispatch dispatch = dispatchDao.findOne(dispatchId);
        Dispatch dispatch_ = new Dispatch();
        if (dispatch!=null)
        {
            dispatch.setType("2");
            dispatch.setStatus("1");
            dispatch_ =   dispatchDao.save(dispatch);
        }
        else
        {
            return null;
        }
        return dispatch_;
    }

}