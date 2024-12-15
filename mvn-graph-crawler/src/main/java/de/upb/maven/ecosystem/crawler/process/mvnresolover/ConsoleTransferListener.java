package de.upb.maven.ecosystem.crawler.process.mvnresolover;

import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

public class ConsoleTransferListener implements
    TransferListener {

  @Override
  public void transferInitiated(TransferEvent transferEvent) throws TransferCancelledException {

  }

  @Override
  public void transferStarted(TransferEvent transferEvent) throws TransferCancelledException {

  }

  @Override
  public void transferProgressed(TransferEvent transferEvent) throws TransferCancelledException {

  }

  @Override
  public void transferCorrupted(TransferEvent transferEvent) throws TransferCancelledException {

  }

  @Override
  public void transferSucceeded(TransferEvent transferEvent) {

  }

  @Override
  public void transferFailed(TransferEvent transferEvent) {

  }
}
