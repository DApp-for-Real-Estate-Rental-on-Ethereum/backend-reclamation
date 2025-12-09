package ma.fstt.reclamationservice.core.blockchain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

@Service
public class BookingPaymentContractService {

    @Value("${app.web3.rpc-url:http://127.0.0.1:8545}")
    private String rpcUrl;

    @Value("${app.web3.contract-address:}")
    private String contractAddress;

    @Value("${app.web3.private-key:}")
    private String privateKey;

    private Web3j web3j;

    private Web3j getWeb3j() {
        if (web3j == null) {
            web3j = Web3j.build(new HttpService(rpcUrl));
        }
        return web3j;
    }

    private Transaction createValidatedCallTransaction(String fromAddress, String toAddress, String data) {
        final String DEFAULT_ADMIN_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        
        String validatedFromAddress = fromAddress;
        if (validatedFromAddress == null || 
            validatedFromAddress.trim().isEmpty() ||
            validatedFromAddress.equals("0x0000000000000000000000000000000000000000") ||
            validatedFromAddress.equals("0x0") ||
            validatedFromAddress.equalsIgnoreCase("null")) {
            validatedFromAddress = DEFAULT_ADMIN_ADDRESS;
        } else {
            validatedFromAddress = validatedFromAddress.trim();
            if (!validatedFromAddress.startsWith("0x")) {
                validatedFromAddress = "0x" + validatedFromAddress;
            }
            validatedFromAddress = validatedFromAddress.toLowerCase();
        }
        
        return new Transaction(
                validatedFromAddress,
                null,
                null,
                null,
                toAddress,
                null,
                data
        );
    }

    private BigInteger getChainId() throws Exception {
        return getWeb3j().ethChainId().send().getChainId();
    }

    public String processReclamationRefund(
            Long bookingId,
            String recipientAddress,
            BigInteger refundAmountWei,
            BigInteger penaltyAmountWei,
            boolean refundFromRent
    ) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured");
        }


        String normalizedPrivateKey = privateKey.trim();
        if (normalizedPrivateKey.startsWith("0x")) {
            normalizedPrivateKey = normalizedPrivateKey.substring(2);
        }
        normalizedPrivateKey = "0x" + normalizedPrivateKey;
        
        Credentials credentials = Credentials.create(normalizedPrivateKey);

        Function function = new Function(
                "processReclamationRefund",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new Address(recipientAddress),
                        new Uint256(refundAmountWei),
                        new Uint256(penaltyAmountWei),
                        new org.web3j.abi.datatypes.Bool(refundFromRent)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        long chainId = getChainId().longValue();

        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }

    public String processPartialRefund(
            Long bookingId,
            String recipientAddress,
            BigInteger refundAmountWei,
            boolean refundFromRent
    ) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured");
        }


        String normalizedPrivateKey = privateKey.trim();
        if (normalizedPrivateKey.startsWith("0x")) {
            normalizedPrivateKey = normalizedPrivateKey.substring(2);
        }
        normalizedPrivateKey = "0x" + normalizedPrivateKey;
        
        Credentials credentials = Credentials.create(normalizedPrivateKey);
        Function function = new Function(
                "processPartialRefund",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new Address(recipientAddress),
                        new Uint256(refundAmountWei),
                        new org.web3j.abi.datatypes.Bool(refundFromRent)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        long chainId = getChainId().longValue();

        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }

    public String setActiveReclamation(Long bookingId, boolean active) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured");
        }

        String normalizedPrivateKey = privateKey.trim();
        if (normalizedPrivateKey.startsWith("0x")) {
            normalizedPrivateKey = normalizedPrivateKey.substring(2);
        }
        normalizedPrivateKey = "0x" + normalizedPrivateKey;
        
        Credentials credentials = Credentials.create(normalizedPrivateKey);
        Function function = new Function(
                "setActiveReclamation",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new org.web3j.abi.datatypes.Bool(active)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        long chainId = getChainId().longValue();

        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }
}

