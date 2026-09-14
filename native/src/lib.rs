use spiritbyte_core::{
    backup, generator,
    vault::{self, Session, VaultPaths},
};
use std::sync::{Arc, Mutex};
use zeroize::Zeroizing;

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum MobileError {
    #[error("{details}")]
    Operation { details: String },
}
type Result<T> = std::result::Result<T, MobileError>;
fn error(e: impl ToString) -> MobileError {
    MobileError::Operation {
        details: e.to_string(),
    }
}

/// A single serialized session. Keys never cross the FFI boundary. Passwords
/// and displayed entry fields necessarily exist briefly in Kotlin memory.
#[derive(uniffi::Object)]
pub struct MobileVault {
    paths: VaultPaths,
    session: Mutex<Option<Session>>,
}

#[uniffi::export]
impl MobileVault {
    #[uniffi::constructor]
    pub fn new(directory: String) -> Arc<Self> {
        Arc::new(Self {
            paths: VaultPaths::new(directory.into()),
            session: Mutex::new(None),
        })
    }

    pub fn exists(&self) -> bool {
        vault::vault_exists(&self.paths)
    }

    pub fn create(&self, password: String) -> Result<String> {
        let password = Zeroizing::new(password);
        if password.chars().count() < 12 {
            return Err(error("Usa al menos 12 caracteres."));
        }
        let mut guard = self.session.lock().map_err(error)?;
        let (session, phrase) = vault::create_vault(&self.paths, &password).map_err(error)?;
        *guard = Some(session);
        Ok(phrase)
    }

    pub fn unlock(&self, password: String) -> Result<()> {
        let password = Zeroizing::new(password);
        let mut guard = self.session.lock().map_err(error)?;
        *guard = None;
        *guard = Some(vault::unlock(&self.paths, &password).map_err(|_| {
            error("No se pudo abrir la bóveda. Revisa la contraseña y los archivos.")
        })?);
        Ok(())
    }

    pub fn recover(&self, phrase: String, password: String) -> Result<()> {
        let phrase = Zeroizing::new(phrase);
        let password = Zeroizing::new(password);
        if password.chars().count() < 12 {
            return Err(error("Usa al menos 12 caracteres."));
        }
        let mut guard = self.session.lock().map_err(error)?;
        *guard = None;
        *guard = Some(vault::recover_and_reset(&self.paths, &phrase, &password).map_err(error)?);
        Ok(())
    }

    pub fn lock(&self) -> Result<()> {
        *self.session.lock().map_err(error)? = None;
        Ok(())
    }

    pub fn entries(&self) -> Result<String> {
        let guard = self.session.lock().map_err(error)?;
        let session = guard.as_ref().ok_or_else(|| error("Bóveda bloqueada."))?;
        serde_json::to_string(&session.data).map_err(error)
    }

    pub fn save_entry(&self, json: String) -> Result<()> {
        let json = Zeroizing::new(json);
        let entry: vault::Entry = serde_json::from_str(&json).map_err(error)?;
        if entry.id.is_empty() || entry.title.trim().is_empty() {
            return Err(error("Falta el título."));
        }
        self.mutate(|data| {
            if let Some(old) = data.entries.iter_mut().find(|e| e.id == entry.id) {
                *old = entry.clone();
            } else {
                data.entries.push(entry);
            }
        })
    }

    pub fn delete_entry(&self, id: String) -> Result<()> {
        self.mutate(|data| data.entries.retain(|e| e.id != id))
    }

    pub fn save_folder(&self, json: String) -> Result<()> {
        let mut folder: vault::Folder = serde_json::from_str(&json).map_err(error)?;
        folder.name = folder.name.trim().to_owned();
        if folder.id.is_empty() || folder.name.is_empty() {
            return Err(error("La carpeta necesita un nombre."));
        }
        self.mutate(|data| {
            if let Some(existing) = data.folders.iter_mut().find(|f| f.id == folder.id) {
                *existing = folder.clone();
            } else { data.folders.push(folder); }
        })
    }

    /// Same semantics as desktop: keep entries and children, move them to root.
    pub fn delete_folder(&self, id: String) -> Result<()> {
        self.mutate(|data| {
            data.folders.retain(|f| f.id != id);
            for folder in &mut data.folders {
                if folder.parent_id.as_deref() == Some(id.as_str()) { folder.parent_id = None; }
            }
            for entry in &mut data.entries {
                if entry.folder_id.as_deref() == Some(id.as_str()) { entry.folder_id = None; }
            }
        })
    }

    pub fn export_backup(&self, password: String) -> Result<String> {
        let password = Zeroizing::new(password);
        let guard = self.session.lock().map_err(error)?;
        let session = guard.as_ref().ok_or_else(|| error("Bóveda bloqueada."))?;
        backup::export(&session.data, &password).map_err(error)
    }

    pub fn import_backup(&self, contents: String, password: String) -> Result<()> {
        if contents.len() > backup::MAX_BACKUP_BYTES {
            return Err(error("El backup supera 32 MiB."));
        }
        let password = Zeroizing::new(password);
        // Keep the lock across derivation so lock() cannot race a later commit.
        let mut guard = self.session.lock().map_err(error)?;
        let session = guard.as_mut().ok_or_else(|| error("Bóveda bloqueada."))?;
        let imported = backup::decrypt(&contents, &password).map_err(error)?;
        let candidate = Session {
            dek: session.dek.clone(),
            data: backup::merge(&session.data, imported),
        };
        vault::write_data(&self.paths, &candidate).map_err(error)?;
        *session = candidate;
        Ok(())
    }

    pub fn generate_password(&self, length: u32) -> String {
        generator::generate(&generator::GenOptions {
            length: length as usize,
            ..Default::default()
        })
    }
}

impl MobileVault {
    fn mutate(&self, action: impl FnOnce(&mut vault::VaultData)) -> Result<()> {
        let mut guard = self.session.lock().map_err(error)?;
        let session = guard.as_mut().ok_or_else(|| error("Bóveda bloqueada."))?;
        let mut candidate = Session {
            dek: session.dek.clone(),
            data: session.data.clone(),
        };
        action(&mut candidate.data);
        backup::validate(&candidate.data).map_err(error)?;
        vault::write_data(&self.paths, &candidate).map_err(error)?;
        *session = candidate;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn mobile_desktop_backup_and_lock() {
        let dir = tempfile::tempdir().unwrap();
        let mobile = MobileVault::new(dir.path().to_string_lossy().into());
        assert!(mobile.entries().is_err());
        let phrase = mobile.create("mobile master password".into()).unwrap();
        mobile
            .save_entry(
                r#"{"id":"one","title":"Correo","password":"secret","createdAt":1,"updatedAt":1}"#
                    .into(),
            )
            .unwrap();
        let encrypted = mobile.export_backup("backup password 123".into()).unwrap();
        let desktop = backup::decrypt(&encrypted, "backup password 123").unwrap();
        assert_eq!(desktop.entries[0].password, "secret");
        mobile
            .import_backup(
                backup::export(&desktop, "backup password 123").unwrap(),
                "backup password 123".into(),
            )
            .unwrap();
        assert_eq!(
            serde_json::from_str::<vault::VaultData>(&mobile.entries().unwrap())
                .unwrap()
                .entries
                .len(),
            2
        );
        mobile.lock().unwrap();
        assert!(mobile.entries().is_err());
        assert!(mobile.unlock("wrong".into()).is_err());
        mobile
            .recover(phrase, "replacement master password".into())
            .unwrap();
        mobile.lock().unwrap();
        mobile.unlock("replacement master password".into()).unwrap();
    }
}
