package com.qcloud.cssg;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.COSEncryptionClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.*;
import com.qcloud.cos.exception.*;
import com.qcloud.cos.model.*;
import com.qcloud.cos.internal.crypto.*;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.utils.DateUtils;
import com.qcloud.cos.transfer.*;
import com.qcloud.cos.model.lifecycle.*;
import com.qcloud.cos.model.inventory.*;
import com.qcloud.cos.model.inventory.InventoryFrequency;

import com.qcloud.util.FileUtil;

import java.io.*;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.net.URL;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class BucketReplication {

    private COSClient cosClient;
    private TransferManager transferManager;

    private String uploadId;
    private List<PartETag> partETags;
    private String localFilePath;

    /**
     * 设置存储桶跨地域复制规则
     */
    public void putBucketReplication() throws InterruptedException, IOException, NoSuchAlgorithmException {
        //.cssg-snippet-body-start:[put-bucket-replication]
        // 源存储桶名称，需包含 appid
        String bucketName = "examplebucket-1250000000";
        
        BucketReplicationConfiguration bucketReplicationConfiguration = new BucketReplicationConfiguration();
        // 设置发起者身份, 格式为： qcs::cam::uin/<OwnerUin>:uin/<SubUin>
        bucketReplicationConfiguration.setRoleName("qcs::cam::uin/100000000001:uin/100000000001");
        
        // 设置目标存储桶和存储类型，QCS 的格式为：qcs::cos:[region]::[bucketname-AppId]
        ReplicationDestinationConfig replicationDestinationConfig = new ReplicationDestinationConfig();
        replicationDestinationConfig.setBucketQCS("qcs::cos:ap-beijing::destinationbucket-1250000000");
        replicationDestinationConfig.setStorageClass(StorageClass.Standard);
        
        // 设置规则状态和前缀
        ReplicationRule replicationRule = new ReplicationRule();
        replicationRule.setStatus(ReplicationRuleStatus.Enabled);
        replicationRule.setPrefix("");
        replicationRule.setDestinationConfig(replicationDestinationConfig);
        // 添加规则
        String ruleId = "replication-to-beijing";
        bucketReplicationConfiguration.addRule(ruleId, replicationRule);
        
        SetBucketReplicationConfigurationRequest setBucketReplicationConfigurationRequest =
                new SetBucketReplicationConfigurationRequest(bucketName, bucketReplicationConfiguration);
        cosClient.setBucketReplicationConfiguration(setBucketReplicationConfigurationRequest);
        
        //.cssg-snippet-body-end
    }

    /**
     * 获取存储桶跨地域复制规则
     */
    public void getBucketReplication() throws InterruptedException, IOException, NoSuchAlgorithmException {
        //.cssg-snippet-body-start:[get-bucket-replication]
        String bucketName = "examplebucket-1250000000";
        
        // 获取存储桶跨地域复制配置方法1
        BucketReplicationConfiguration brcfRet = cosClient.getBucketReplicationConfiguration(bucketName);
        
        // 获取存储桶跨地域复制配置方法2
        BucketReplicationConfiguration brcfRet2 = cosClient.getBucketReplicationConfiguration(
                new GetBucketReplicationConfigurationRequest(bucketName));
        
        //.cssg-snippet-body-end
    }

    /**
     * 删除存储桶跨地域复制规则
     */
    public void deleteBucketReplication() throws InterruptedException, IOException, NoSuchAlgorithmException {
        //.cssg-snippet-body-start:[delete-bucket-replication]
        String bucketName = "examplebucket-1250000000";
        
        // 删除存储桶跨地域复制配置方法1
        cosClient.deleteBucketReplicationConfiguration(bucketName);
        
        // 删除存储桶跨地域复制配置方法2
        cosClient.deleteBucketReplicationConfiguration(new DeleteBucketReplicationConfigurationRequest(bucketName));
        
        //.cssg-snippet-body-end
    }

    // .cssg-methods-pragma

    private void initClient() {
        String secretId = "COS_SECRETID";
        String secretKey = "COS_SECRETKEY";
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 2 设置 bucket 的区域, COS 地域的简称请参照 https://cloud.tencent.com/document/product/436/6224
        // clientConfig 中包含了设置 region, https(默认 http), 超时, 代理等 set 方法, 使用可参见源码或者常见问题 Java SDK 部分。
        Region region = new Region("COS_REGION");
        ClientConfig clientConfig = new ClientConfig(region);
        // 3 生成 cos 客户端。
        this.cosClient = new COSClient(cred, clientConfig);

        // 高级接口传输类
        // 线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        ExecutorService threadPool = Executors.newFixedThreadPool(32);
        // 传入一个 threadpool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        transferManager = new TransferManager(cosClient, threadPool);
        // 设置高级接口的分块上传阈值和分块大小为10MB
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(10 * 1024 * 1024);
        transferManagerConfiguration.setMinimumUploadPartSize(10 * 1024 * 1024);
        transferManager.setConfiguration(transferManagerConfiguration);
    }

    public static void main(String[] args) throws InterruptedException, IOException,        NoSuchAlgorithmException {
        BucketReplication example = new BucketReplication();
        example.initClient();

        // 设置存储桶跨地域复制规则
        example.putBucketReplication();

        // 获取存储桶跨地域复制规则
        example.getBucketReplication();

        // 删除存储桶跨地域复制规则
        example.deleteBucketReplication();

        // .cssg-methods-pragma

        // 使用完成之后销毁 Client，建议 Client 保持为单例
        example.cosClient.shutdown();
    }

}